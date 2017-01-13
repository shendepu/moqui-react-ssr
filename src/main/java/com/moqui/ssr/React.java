package com.moqui.ssr;

import jdk.nashorn.api.scripting.NashornScriptEngine;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;

import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import javax.script.*;
import javax.servlet.http.HttpServletRequest;

import org.moqui.context.ExecutionContext;
import org.moqui.context.ExecutionContextFactory;
import org.moqui.resource.ResourceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class React {
    private final static Logger logger = LoggerFactory.getLogger(React.class);

    private static final NashornScriptEngine nashornEngine;
    private static final Consumer<Object> consoleLogInfo = object -> JavascriptLogger.logger.info("{}", object);
    private static final Consumer<Object> consoleLogError = object -> JavascriptLogger.logger.error("{}", object);

    private ExecutionContextFactory ecf;
    private String basePath;
    private Map<String, Map<String, Object>> appJsFileMap;
    private int jsWaitTimeout = 20 * 1000; // 20s
    private static int jsWaitInterval = 30; // 30ms

    private Map<String, CompiledScript> compiledScriptMap = new LinkedHashMap<>();
    private Map<String, CompiledScript> compiledScriptRunOnceMap = new LinkedHashMap<>();

    private ThreadLocal<ReactRender> activeRender = new ThreadLocal<>();

    private static ScheduledExecutorService globalScheduledThreadPool = Executors.newScheduledThreadPool(20);

    private Bindings initialBindings;

    static {
        NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
        nashornEngine = (NashornScriptEngine) factory.getScriptEngine();
    }

    React(ExecutionContextFactory ecf, String basePath, Map<String, Map<String, Object>> appJsFileMap,
            Map<String, Object> optionMap, Map<String, Object> poolConfig) {
        this.ecf = ecf;
        this.basePath = basePath;
        this.appJsFileMap = appJsFileMap;
        if (optionMap.containsKey("jsTimeout")) jsWaitTimeout = (int) optionMap.get("jsTimeout");

        initNashornEngine();
    }

    synchronized private void initNashornEngine() {
        ScriptContext sc = new SimpleScriptContext();

        sc.setBindings(nashornEngine.createBindings(), ScriptContext.ENGINE_SCOPE);
        sc.setAttribute("consoleLogInfo", consoleLogInfo, ScriptContext.ENGINE_SCOPE);
        sc.setAttribute("consoleLogError", consoleLogError, ScriptContext.ENGINE_SCOPE);
        sc.setAttribute("__IS_SSR__", true, ScriptContext.ENGINE_SCOPE);
        sc.setAttribute("__APP_BASE_PATH__", basePath, ScriptContext.ENGINE_SCOPE);
        sc.setAttribute("__NASHORN_POLYFILL_TIMER__", globalScheduledThreadPool, ScriptContext.ENGINE_SCOPE);

        initialBindings = sc.getBindings(ScriptContext.ENGINE_SCOPE);

        for (Map.Entry<String, Map<String, Object>> entry : appJsFileMap.entrySet()) {
            if (entry.getValue() == null) continue;
            logger.info("Compiling " + entry.getKey());

            try {
                ResourceReference fileRr = (ResourceReference) entry.getValue().get("resourceReference");
                // runOnce default to false
                boolean runOnce = entry.getValue().get("runOnce") != null && (boolean) entry.getValue().get("runOnce");
                CompiledScript cs = nashornEngine.compile(new InputStreamReader(fileRr.openStream()));

                if (runOnce) compiledScriptRunOnceMap.put(entry.getKey(), cs);
                else compiledScriptMap.put(entry.getKey(), cs);

            } catch (ScriptException e) {
                logger.error("Fail to compile script " + entry.getValue());
                throw new RuntimeException(e);
            }
        }
    }

    private ReactRender getReactRender() {
        ReactRender render = activeRender.get();
        if (render != null) return render;

        render = new ReactRender(this, compiledScriptRunOnceMap, nashornEngine, initialBindings);
        this.activeRender.set(render);
        return render;
    }

    public Map<String, Object> render(HttpServletRequest request) {
        ReactRender render = getReactRender();
        return render.render(request, compiledScriptMap, jsWaitTimeout, jsWaitInterval);
    }

    public ExecutionContext getExecutionContext() {
        return ecf.getExecutionContext();
    }
//
//    private static void printMap(String name, Map<String, Object> map) {
//        System.out.println("==============" + name + "==============");
//        for (Map.Entry<String, Object> entry : map.entrySet()) {
//            System.out.print(entry.getKey());
//            System.out.print(":");
//            System.out.print(entry.getValue());
//            System.out.print(" ");
//        }
//        System.out.println();
//    }
}