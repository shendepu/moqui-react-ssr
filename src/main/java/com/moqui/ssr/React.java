package com.moqui.ssr;

import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.NashornScriptEngine;
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
        System.out.println("======== resolve promise");
        System.out.println(object);
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

//    React(ExecutionContext ec, Map<String, ResourceReference> jsFileMap) {
//        this.ec = ec;
//        this.jsFileMap = jsFileMap;
//        initNashornEngine();
//    }

//    private ThreadLocal<NashornScriptEngine> engineHolder = new ThreadLocal<NashornScriptEngine>() {
//        @Override
//        protected NashornScriptEngine initialValue() {
//            NashornScriptEngine nashornScriptEngine = (NashornScriptEngine) new ScriptEngineManager().getEngineByName("nashorn");
//
//            Consumer<Object> println = System.out::println;
//            Consumer<Object> printlnString = object -> System.out.println(object.toString());
//            ScriptContext sc = nashornScriptEngine.getContext();
//            sc.setAttribute("println", println, ScriptContext.GLOBAL_SCOPE);
//            sc.setAttribute("printlnString", printlnString, ScriptContext.GLOBAL_SCOPE);
//
//            return nashornScriptEngine;
//        }
//    };

    private void initNashornEngine() {
        nashornEngine = (NashornScriptEngine) new ScriptEngineManager().getEngineByName("nashorn");

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

//        try {
//            for (Map.Entry<String, CompiledScript> entry : compiledScriptMap.entrySet()) {
//                ec.getLogger().info("Init evaluating " + entry.getKey());
//                entry.getValue().eval();
//            }
//        } catch (ScriptException e) {
//            ec.getLogger().error("Fail to eval script at init");
//            throw new RuntimeException(e);
//        }
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

        ec.getLogger().info("==== default context");
        ec.getLogger().info(new PrettyPrintingMap<>(defaultScriptContext.getBindings(ScriptContext.ENGINE_SCOPE)).toString(2));
        Map<String, Object> result = new HashMap(2);
        result.put("html", null);
        result.put("state", null);
        try {
            ScriptContext sc = new SimpleScriptContext();

            ec.getLogger().info("==== engine context 0");
            ec.getLogger().info(new PrettyPrintingMap<>(nashornEngine.getContext().getBindings(ScriptContext.ENGINE_SCOPE)).toString(2));

//            sc.setBindings(nashornEngine.createBindings(), ScriptContext.ENGINE_SCOPE);
//            sc.getBindings(ScriptContext.ENGINE_SCOPE).putAll(defaultScriptContext.getBindings(ScriptContext.ENGINE_SCOPE));
            sc.setBindings(defaultScriptContext.getBindings(ScriptContext.ENGINE_SCOPE), ScriptContext.ENGINE_SCOPE);
            nashornEngine.setContext(sc);

//            ScriptContext sc = nashornEngine.getContext();
            try {

                String locationUrl = getUrlLocation(request);

                sc.setAttribute("__REQ_URL__", locationUrl, ScriptContext.ENGINE_SCOPE);
                ec.getLogger().info(locationUrl);

//                for (Map.Entry<String, ResourceReference> entry : appJsFileMap.entrySet()) {
//                    if (entry.getValue() == null) continue;
//                    ec.getLogger().info("Evaluating " + entry.getKey());
//
//                    ec.getLogger().info("==== sc context - before " + entry.getKey());
//                    ec.getLogger().info(new PrettyPrintingMap<>(sc.getBindings(ScriptContext.ENGINE_SCOPE)).toString(2));
//
//                    nashornEngine.eval(new InputStreamReader(entry.getValue().openStream()), sc);
//                }
                for (Map.Entry<String, CompiledScript> entry : compiledScriptMap.entrySet()) {
                    ec.getLogger().info("Evaluating " + entry.getKey());
                    ec.getLogger().info("==== sc context - before " + entry.getKey());
                    ec.getLogger().info(new PrettyPrintingMap<>(sc.getBindings(ScriptContext.ENGINE_SCOPE)).toString(2));

                    entry.getValue().eval(sc);
                }

            } catch (ScriptException e) {
                ec.getLogger().error(e.getMessage());
                throw new RuntimeException(e);
            }

            ec.getLogger().info("start server rendering");
            promiseResolved = false;

//            JSObject renderServerFunc = (JSObject) sc.getAttribute("renderServer");
//            System.out.println("--- debuging");
//            System.out.println(sc);
//            System.out.println(renderServerFunc);
//            JSObject promise = (JSObject) renderServerFunc.call(null);
//            System.out.println(promise);

//            ScriptObjectMirror promise = (ScriptObjectMirror) nashornEngine.invokeFunction("renderServer");
            ScriptObjectMirror app = (ScriptObjectMirror) nashornEngine.invokeFunction("newApp");
            ScriptObjectMirror promise = (ScriptObjectMirror) app.callMember("render");

//            JSObject then = (JSObject) promise.getMember("then");
//            then.call(renderServerFunc, fnResolve, fnReject);
            promise.callMember("then", fnResolve, fnReject);


            int interval = 100;
            int i = 1;
            while (!promiseResolved && i < 20) {
                ec.getLogger().info("---- sleep " + Integer.toString(interval) + " ms... " + Integer.toString(i));
                i = i + 1;
                Thread.sleep(interval);
            }

            ec.getLogger().info("==== sc context");
            ec.getLogger().info(new PrettyPrintingMap<>(sc.getBindings(ScriptContext.ENGINE_SCOPE)).toString(2));
            ec.getLogger().info("==== engine context");
            ec.getLogger().info(new PrettyPrintingMap<>(nashornEngine.getContext().getBindings(ScriptContext.ENGINE_SCOPE)).toString(2));
            result.put("html", html);
//            result.put("state", nashornEngine.invokeFunction("getState"));
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