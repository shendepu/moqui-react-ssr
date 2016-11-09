package com.moqui.ssr;

import jdk.nashorn.api.scripting.NashornScriptEngine;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import jdk.nashorn.api.scripting.ScriptObjectMirror;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;

import javax.script.*;
import javax.servlet.http.HttpServletRequest;

import org.moqui.resource.ResourceReference;
import org.moqui.context.ExecutionContext;

public class React {
    private NashornScriptEngine nashornEngine;

    private ExecutionContext ec;
    private Map<String, ResourceReference> appJsFileMap;

    private Map<String, CompiledScript> compiledScriptMap = new LinkedHashMap();

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

    React(ExecutionContext ec, Map<String, ResourceReference> appJsFileMap) {
        this.ec = ec;
        this.appJsFileMap = appJsFileMap;

        initNashornEngine();
    }

    private void initNashornEngine() {
        NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
        nashornEngine = (NashornScriptEngine) factory.getScriptEngine("--global-per-engine");

        ScriptContext defaultScriptContext = nashornEngine.getContext();
        defaultScriptContext.setAttribute("println", println, ScriptContext.ENGINE_SCOPE);
        defaultScriptContext.setAttribute("printlnString", printlnString, ScriptContext.ENGINE_SCOPE);
        defaultScriptContext.setAttribute("ec", ec, ScriptContext.ENGINE_SCOPE);
        defaultScriptContext.setAttribute("__APP_BASE_PATH__", ec.getContext().get("basePath"), ScriptContext.ENGINE_SCOPE);
        defaultScriptContext.setAttribute("__IS_SSR__", true, ScriptContext.ENGINE_SCOPE);

        for (Map.Entry<String, ResourceReference> entry : appJsFileMap.entrySet()) {
            if (entry.getValue() == null) continue;
            ec.getLogger().info("Compiling " + entry.getKey());

            try {
                CompiledScript cs = nashornEngine.compile(new InputStreamReader(entry.getValue().openStream()));
                compiledScriptMap.put(entry.getKey(), cs);
            } catch (ScriptException e) {
                ec.getLogger().error("Fail to compile script " + entry.getValue());
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

        Map<String, Object> result = new HashMap(2);
        result.put("html", null);
        result.put("state", null);
        nashornEngine.setBindings(nashornEngine.createBindings(), ScriptContext.ENGINE_SCOPE);
        try {
            ScriptContext sc = nashornEngine.getContext();

            try {
                String locationUrl = getUrlLocation(request);
                sc.setAttribute("__REQ_URL__", locationUrl, ScriptContext.ENGINE_SCOPE);
                ec.getLogger().info(locationUrl);

                for (Map.Entry<String, CompiledScript> entry : compiledScriptMap.entrySet()) {
                    ec.getLogger().info("Evaluating " + entry.getKey());
                    entry.getValue().eval(sc);
                }

            } catch (ScriptException e) {
                ec.getLogger().error(e.getMessage());
                throw new RuntimeException(e);
            }

            ec.getLogger().info("start server rendering");
            promiseResolved = false;

            ScriptObjectMirror app = (ScriptObjectMirror) nashornEngine.invokeFunction("newApp");
            ScriptObjectMirror promise = (ScriptObjectMirror) app.callMember("render");
            promise.callMember("then", fnResolve, fnReject);

            int interval = 5;
            int i = 1;
            while (!promiseResolved && i < 1000) {
                ec.getLogger().info("---- sleep " + Integer.toString(interval) + " ms... " + Integer.toString(i));
                i = i + 1;
                Thread.sleep(interval);
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

    private static StringBuilder getStringBuilderFromInputStream(InputStream is) {
        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            br = new BufferedReader(new InputStreamReader(is));
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return sb;
    }

    private static List<Object> filteredNames = new ArrayList<>();
    static {
        filteredNames.add("global");
        filteredNames.add("__APP_BASE_PATH__");
        filteredNames.add("__IS_SSR__");
        filteredNames.add("__APP_BASE_PATH__");
        filteredNames.add("ec");
        filteredNames.add("println");
    }

    public class PrettyPrintingMap<K, V> {
        private Map<K, V> map;


        public PrettyPrintingMap(Map<K, V> map) {
            this.map = map;
        }

        public String toString() {
            return toString(1);
        }

        public String toString(int level) {
            if (map == null) return "";
            StringBuilder sb = new StringBuilder();
            Iterator<Map.Entry<K, V>> iter = map.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<K, V> entry = iter.next();
                if (!filteredNames.contains(entry.getKey())) continue;
                sb.append(entry.getKey());
                sb.append('=').append('"');
                if (level > 1 && entry.getValue() instanceof Map) {
                    sb.append(new PrettyPrintingMap<>((Map) entry.getValue()).toString(level - 1));
                } else {
                    sb.append(entry.getValue());
                }
                sb.append('"');
                if (iter.hasNext()) {
                    sb.append(',').append(' ');
                }
            }
            return sb.toString();

        }
    }
}