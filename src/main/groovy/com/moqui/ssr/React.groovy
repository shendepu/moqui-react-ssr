package com.moqui.ssr

import jdk.nashorn.api.scripting.NashornScriptEngine
import jdk.nashorn.api.scripting.ScriptObjectMirror
import jdk.nashorn.api.scripting.JSObject
import java.util.function.Consumer
import java.util.function.Function

import javax.script.ScriptEngineManager
import javax.script.ScriptException
import javax.script.ScriptContext;
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.util.List
import org.moqui.resource.ResourceReference
import org.moqui.context.ExecutionContext
import com.fasterxml.jackson.databind.ObjectMapper

public class React {
    private ExecutionContext ec;
    private Map<String, ResourceReference> jsFileMap;

    private Object html;
    private Object error;
    private boolean promiseResolved;

    React(ExecutionContext ec, Map<String, ResourceReference> jsFileMap) {
        this.ec = ec;
        this.jsFileMap = jsFileMap;
    }

    private ThreadLocal<NashornScriptEngine> engineHolder = new ThreadLocal<NashornScriptEngine>() {
        @Override
        protected NashornScriptEngine initialValue() {
            NashornScriptEngine nashornScriptEngine = (NashornScriptEngine) new ScriptEngineManager().getEngineByName("nashorn");
            try {
                for (ResourceReference rr : jsFileMap.values()) {
                    if (rr == null) continue;
                    ec.getLogger().info("jsFileMap: " + rr.getFileName());
                }

                Consumer<String> println = { s -> System.out.println(s) };
                ScriptContext sc = nashornScriptEngine.getContext();
                sc.setAttribute("println", println, ScriptContext.ENGINE_SCOPE);
                sc.setAttribute("ec", ec, ScriptContext.ENGINE_SCOPE);

                String base = "/Users/jimmy/workspace/opensource/moqui/moqui-framework/runtime/component"
//                for (Map.Entry<String, ResourceReference> entry : jsFileMap.entrySet()) {
//                    if (entry.getValue() == null) continue;
//                    ec.getLogger().info("Evaluating " + entry.getKey());
//
//                    Object o = nashornScriptEngine.eval(new InputStreamReader(entry.getValue().openStream()));
//                    // nashornScriptEngine.eval("load('${base}/${entry.getValue().getLocation().substring(12)}')");
//                }
                nashornScriptEngine.eval("load('js/static/nashorn-polyfill.js')")
                nashornScriptEngine.eval("load('js/dist/vendor.bd94e9562481625e194c.js')")
                nashornScriptEngine.eval("load('js/static/nashorn-setTimeout.js')")
                nashornScriptEngine.eval("load('js/dist/app.baa74eaf1a3020369dfa.js')")
                nashornScriptEngine.eval("load('js/static/print-app.js')")

            } catch (ScriptException e) {
                throw new RuntimeException(e);
            }
            return nashornScriptEngine;
        }
    };

    public Object getState() {
        try {
            ec.getLogger().info("start server rendering getState");
            Object state = engineHolder.get().invokeFunction("getState");
            return state;
        } catch (Exception e) {
            throw new IllegalStateException("failed to get store state", e);
        }
    }

    public String render() {
        try {
            ec.getLogger().info("start server rendering");
            promiseResolved = false;

            // Object html = engineHolder.get().invokeFunction("renderServer")
            ScriptObjectMirror promise = (ScriptObjectMirror) engineHolder.get().invokeFunction("renderServer");
            Consumer<Object> fnResolve = { object ->
                html = object;
                System.out.println("------ from fnResolve (groovy)");
                System.out.println(String.valueOf(html));
            };

            System.out.println(fnResolve);

            Consumer<Object> fnReject = { object ->
                error = object;
                System.out.println("------ from fnReject");
                System.out.println(String.valueOf(error));
            };
            promise.callMember("then", fnResolve, fnReject);

            Thread.sleep(2000);

            System.out.println(promise.getMember("catch").getClass())

            return String.valueOf(html);
        } catch (Exception e) {
            throw new IllegalStateException("failed to render react", e);
        }
    }

    private Reader read(String location) {
        ResourceReference rr = ec.getResource().getLocationReference("component://react-ssr/screen/ReactSSRRoot/" + location);
        InputStream inputStream = rr.openStream();
        return new InputStreamReader(inputStream);
    }
}