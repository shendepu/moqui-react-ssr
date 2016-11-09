package com.moqui.ssr;

import jdk.nashorn.api.scripting.NashornScriptEngine;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import jdk.nashorn.api.scripting.ScriptObjectMirror;

import java.io.InputStreamReader;
import java.util.HashMap;
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

    private Object html;
    private Object error;
    private boolean promiseResolved;
    private final Object promiseLock = new Object();

    private Consumer<Object> fnResolve = object -> {
        synchronized (promiseLock) {
            html = object;
            promiseResolved = true;
        }
    };

    private Consumer<Object> fnReject = object -> {
        synchronized (promiseLock) {
            error = object;
            promiseResolved = true;
        }
    };

    private Consumer<Object> println = System.out::println;
    private Consumer<Object> printlnString = object -> System.out.println(object.toString());

    React(ExecutionContextFactory ecf, String basePath, Map<String, ResourceReference> appJsFileMap, Map<String, Object> optionMap) {
        this.ecf = ecf;
        this.basePath = basePath;
        this.appJsFileMap = appJsFileMap;
        if (optionMap.containsKey("jsTimeout")) {
            jsWaitRetryTimes = (int) optionMap.get("jsTimeout") / jsWaitInterval + 1;
        }
        System.out.println(Integer.toString(jsWaitRetryTimes) + "wait timeout");
        System.out.println(Integer.toString(jsWaitInterval) + "wait interval");
        initNashornEngine();
    }

    private void initNashornEngine() {
        NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
        nashornEngine = (NashornScriptEngine) factory.getScriptEngine("--global-per-engine");

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

    public Object getState() {
        try {
            return nashornEngine.invokeFunction("getState");
        } catch (Exception e) {
            throw new IllegalStateException("failed to get store state", e);
        }
    }

    public Map<String, Object> render(HttpServletRequest request) {
        ScriptContext defaultScriptContext = nashornEngine.getContext();

        Map<String, Object> result = new HashMap<>(2);
        result.put("html", null);
        result.put("state", null);
        nashornEngine.setBindings(nashornEngine.createBindings(), ScriptContext.ENGINE_SCOPE);
        try {
            ScriptContext sc = nashornEngine.getContext();

            try {
                String locationUrl = getUrlLocation(request);
                sc.setAttribute("__REQ_URL__", locationUrl, ScriptContext.ENGINE_SCOPE);

                for (Map.Entry<String, CompiledScript> entry : compiledScriptMap.entrySet()) {
                    entry.getValue().eval(sc);
                }

            } catch (ScriptException e) {
                ecf.getExecutionContext().getLogger().error(e.getMessage());
                throw new RuntimeException(e);
            }

            promiseResolved = false;

            ScriptObjectMirror app = (ScriptObjectMirror) nashornEngine.invokeFunction("newApp");
            ScriptObjectMirror promise = (ScriptObjectMirror) app.callMember("render");
            promise.callMember("then", fnResolve, fnReject);

            int i = 1;
            while (!promiseResolved && i < jsWaitRetryTimes) {
                System.out.println("---- sleep " + Integer.toString(jsWaitRetryTimes) + " ms... " + Integer.toString(i));
                i = i + 1;
                Thread.sleep(jsWaitInterval);
            }

            result.put("html", html);
            result.put("state", app.callMember("getState"));

        } catch (Exception e) {
            throw new IllegalStateException("failed to render react", e);
        } finally {
            nashornEngine.setContext(defaultScriptContext);
        }
        return result;
    }

    public void destroy() {

    }

    public static String getUrlLocation(HttpServletRequest request) {
        StringBuilder requestUrl = new StringBuilder();
        requestUrl.append(request.getRequestURI());
        if (request.getQueryString() != null && request.getQueryString().length() > 0) requestUrl.append("?" + request.getQueryString());
        return requestUrl.toString();
    }
}