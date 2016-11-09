package com.moqui.ssr;

import jdk.nashorn.api.scripting.NashornScriptEngine;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;

import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.servlet.http.HttpServletRequest;

import org.moqui.context.ExecutionContextFactory;
import org.moqui.resource.ResourceReference;

public class React {
    private NashornScriptEngine nashornEngine;

    private ExecutionContextFactory ecf;
    private String basePath;
    private Map<String, ResourceReference> appJsFileMap;
    private int jsWaitRetryTimes = 1000;   // wait 5ms * 1000 = 5s
    private static int jsWaitInterval = 5; // 5ms

    private Map<String, CompiledScript> compiledScriptMap = new LinkedHashMap<>();

    private ThreadLocal<ReactRender> activeRender = new ThreadLocal<>();
    private Consumer<Object> println = System.out::println;
    private Consumer<Object> printlnString = object -> System.out.println(object.toString());

    React(ExecutionContextFactory ecf, String basePath, Map<String, ResourceReference> appJsFileMap, Map<String, Object> optionMap) {
        this.ecf = ecf;
        this.basePath = basePath;
        this.appJsFileMap = appJsFileMap;
        if (optionMap.containsKey("jsTimeout")) {
            jsWaitRetryTimes = (int) optionMap.get("jsTimeout") / jsWaitInterval + 1;
        }
        initNashornEngine();
    }

    private void initNashornEngine() {
        NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
        nashornEngine = (NashornScriptEngine) factory.getScriptEngine();

        ScriptContext defaultScriptContext = nashornEngine.getContext();
        defaultScriptContext.setAttribute("println", println, ScriptContext.ENGINE_SCOPE);
        defaultScriptContext.setAttribute("printlnString", printlnString, ScriptContext.ENGINE_SCOPE);
        defaultScriptContext.setAttribute("__APP_BASE_PATH__", basePath, ScriptContext.ENGINE_SCOPE);
        defaultScriptContext.setAttribute("__IS_SSR__", true, ScriptContext.ENGINE_SCOPE);

        for (Map.Entry<String, ResourceReference> entry : appJsFileMap.entrySet()) {
            if (entry.getValue() == null) continue;
            ecf.getExecutionContext().getLogger().info("Compiling " + entry.getKey());

            try {
                CompiledScript cs = nashornEngine.compile(new InputStreamReader(entry.getValue().openStream()));
                compiledScriptMap.put(entry.getKey(), cs);
            } catch (ScriptException e) {
                ecf.getExecutionContext().getLogger().error("Fail to compile script " + entry.getValue());
                throw new RuntimeException(e);
            }
        }
    }

    private ReactRender getReactRender() {
        ReactRender render = activeRender.get();
        if (render != null) return render;

        render = new ReactRender();
        this.activeRender.set(render);
        return render;
    }

    public Map<String, Object> render(HttpServletRequest request) {
        ReactRender render = getReactRender();
        return render.render(request, nashornEngine, compiledScriptMap, jsWaitRetryTimes, jsWaitInterval);
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