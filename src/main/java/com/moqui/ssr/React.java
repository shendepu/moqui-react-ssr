package com.moqui.ssr;

import jdk.nashorn.api.scripting.NashornScriptEngine;
import jdk.nashorn.api.scripting.ScriptObjectMirror;

import java.util.Map;
import java.util.function.Consumer;

import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.ScriptContext;
import java.io.InputStreamReader;

import org.moqui.resource.ResourceReference;
import org.moqui.context.ExecutionContext;

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

                Consumer<Object> println = System.out::println;
                ScriptContext sc = nashornScriptEngine.getContext();
                sc.setAttribute("println", println, ScriptContext.ENGINE_SCOPE);
                sc.setAttribute("ec", ec, ScriptContext.ENGINE_SCOPE);

                for (Map.Entry<String, ResourceReference> entry : jsFileMap.entrySet()) {
                    if (entry.getValue() == null) continue;
                    ec.getLogger().info("Evaluating " + entry.getKey());

                    nashornScriptEngine.eval(new InputStreamReader(entry.getValue().openStream()));
                }

            } catch (ScriptException e) {
                throw new RuntimeException(e);
            }
            return nashornScriptEngine;
        }
    };

    public Object getState() {
        try {
            return engineHolder.get().invokeFunction("getState");
        } catch (Exception e) {
            throw new IllegalStateException("failed to get store state", e);
        }
    }

    public String render() {
        try {
            ec.getLogger().info("start server rendering");
            promiseResolved = false;

            ScriptObjectMirror promise = (ScriptObjectMirror) engineHolder.get().invokeFunction("renderServer");
            Consumer<Object> fnResolve = object -> {
                    promiseResolved = true;
                    html = object;
            };

            System.out.println(fnResolve);

            Consumer<Object> fnReject = object -> {

                promiseResolved = true;
                    error = object;
            };
            promise.callMember("then", fnResolve, fnReject);

            int interval = 50;
            int i = 1;
            while (!promiseResolved && i < 20) {
                ec.getLogger().info("---- sleep " + Integer.toString(interval) + " ms... " + Integer.toString(i));
                i = i + 1;
                Thread.sleep(interval);
            }

            return String.valueOf(html);
        } catch (Exception e) {
            throw new IllegalStateException("failed to render react", e);
        }
    }
}