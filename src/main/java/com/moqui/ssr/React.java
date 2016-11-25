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

import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.moqui.context.ExecutionContextFactory;
import org.moqui.resource.ResourceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class React {
    private final static Logger logger = LoggerFactory.getLogger(React.class);

    private NashornScriptEngine nashornEngine;

    private ExecutionContextFactory ecf;
    private String basePath;
    private Map<String, Map<String, Object>> appJsFileMap;
    private int jsWaitRetryTimes = 1000;   // wait 20ms * 1000 = 20s
    private static int jsWaitInterval = 20; // 20ms

    private Map<String, CompiledScript> compiledScriptMap = new LinkedHashMap<>();
    private Map<String, CompiledScript> compiledScriptRunOnceMap = new LinkedHashMap<>();

    private ThreadLocal<ReactRender> activeRender = new ThreadLocal<>();
    private static final Consumer<Object> consoleLogInfo = object -> JavascriptLogger.logger.info("{}", object);
    private static final Consumer<Object> consoleLogError = object -> JavascriptLogger.logger.error("{}", object);

    private ObjectPool<ScriptContext> scriptContextPool;

    React(ExecutionContextFactory ecf, String basePath, Map<String, Map<String, Object>> appJsFileMap,
            Map<String, Object> optionMap, Map<String, Object> poolConfig) {
        this.ecf = ecf;
        this.basePath = basePath;
        this.appJsFileMap = appJsFileMap;
        if (optionMap.containsKey("jsTimeout")) {
            jsWaitRetryTimes = (int) optionMap.get("jsTimeout") / jsWaitInterval + 1;
        }
        initNashornEngine();
        initScriptContextPool(poolConfig);
    }

    private void initNashornEngine() {
        NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
        nashornEngine = (NashornScriptEngine) factory.getScriptEngine();

        ScriptContext defaultScriptContext = nashornEngine.getContext();
        defaultScriptContext.setAttribute("consoleLogInfo", consoleLogInfo, ScriptContext.ENGINE_SCOPE);
        defaultScriptContext.setAttribute("consoleLogError", consoleLogError, ScriptContext.ENGINE_SCOPE);
        defaultScriptContext.setAttribute("__APP_BASE_PATH__", basePath, ScriptContext.ENGINE_SCOPE);
        defaultScriptContext.setAttribute("__IS_SSR__", true, ScriptContext.ENGINE_SCOPE);

        for (Map.Entry<String, Map<String, Object>> entry : appJsFileMap.entrySet()) {
            if (entry.getValue() == null) continue;
            ecf.getExecutionContext().getLogger().info("Compiling " + entry.getKey());

            try {
                ResourceReference fileRr = (ResourceReference) entry.getValue().get("resourceReference");
                // runOnce default to false
                boolean runOnce = entry.getValue().get("runOnce") != null && (boolean) entry.getValue().get("runOnce");
                CompiledScript cs = nashornEngine.compile(new InputStreamReader(fileRr.openStream()));

                if (runOnce) compiledScriptRunOnceMap.put(entry.getKey(), cs);
                else compiledScriptMap.put(entry.getKey(), cs);

            } catch (ScriptException e) {
                ecf.getExecutionContext().getLogger().error("Fail to compile script " + entry.getValue());
                throw new RuntimeException(e);
            }
        }
    }

    private void initScriptContextPool(Map<String, Object> poolConfigMap) {
        int minIdle = poolConfigMap.get("minIdle") != null ? (int) poolConfigMap.get("minIdle") : 8;
        long maxWait = poolConfigMap.get("maxWait") != null ? (long) poolConfigMap.get("maxWait") : 20;
        int maxIdle = poolConfigMap.get("maxIdle") != null ? (int) poolConfigMap.get("maxIdle") : 10;
        int maxTotal = poolConfigMap.get("maxTotal") != null ? (int) poolConfigMap.get("maxTotal") : 100;
        boolean blockWhenExhausted = poolConfigMap.get("blockWhenExhausted") == null || (boolean) poolConfigMap.get("blockWhenExhausted");
        boolean lifo = poolConfigMap.get("lifo") == null || (boolean) poolConfigMap.get("lifo");

        GenericObjectPoolConfig config = new GenericObjectPoolConfig();
        config.setMaxIdle(maxIdle);
        config.setMinIdle(minIdle);
        config.setMaxTotal(maxTotal);
        config.setMaxWaitMillis(maxWait);

        if (poolConfigMap.get("minEvictableIdleTimeMillis") !=null)
            config.setMinEvictableIdleTimeMillis((long) poolConfigMap.get("minEvictableIdleTimeMillis"));
        if (poolConfigMap.get("numTestsPerEvictionRun") != null)
            config.setNumTestsPerEvictionRun((int) poolConfigMap.get("numTestsPerEvictionRun"));
        if (poolConfigMap.get("testOnBorrow") != null) config.setTestOnBorrow((boolean) poolConfigMap.get("testOnBorrow"));
        if (poolConfigMap.get("testOnReturn") != null) config.setTestOnReturn((boolean) poolConfigMap.get("testOnReturn"));
        if (poolConfigMap.get("testWhileIdle") != null) config.setTestWhileIdle((boolean) poolConfigMap.get("testWhileIdle"));
        if (poolConfigMap.get("timeBetweenEvictionRunsMillis") != null)
            config.setTimeBetweenEvictionRunsMillis((long) poolConfigMap.get("timeBetweenEvictionRunsMillis"));

        config.setBlockWhenExhausted(blockWhenExhausted);
        config.setLifo(lifo);
        this.scriptContextPool = new GenericObjectPool<>(new GlobalMirrorFactory(nashornEngine, compiledScriptRunOnceMap), config);
    }

    private ReactRender getReactRender() {
        ReactRender render = activeRender.get();
        if (render != null) return render;

        render = new ReactRender(this);
        this.activeRender.set(render);
        return render;
    }

    public ObjectPool<ScriptContext> getScriptContextPool() {
        return this.scriptContextPool;
    }

    public Map<String, Object> render(HttpServletRequest request) {
        ReactRender render = getReactRender();
        return render.render(request, compiledScriptMap, jsWaitRetryTimes, jsWaitInterval);
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