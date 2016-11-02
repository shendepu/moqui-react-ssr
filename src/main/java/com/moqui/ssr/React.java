package com.moqui.ssr;

import jdk.nashorn.api.scripting.NashornScriptEngine;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.api.scripting.JSObject;

import java.util.Map;
import java.util.function.Consumer;

import javax.script.ScriptContext;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;

import org.moqui.resource.ResourceReference;
import org.moqui.context.ExecutionContext;
import com.fasterxml.jackson.databind.ObjectMapper;

public class React {
    private ExecutionContext ec;
    private Map<String, ResourceReference> jsFileMap;

    private Object html;
    private Object error;

    React(ExecutionContext ec, Map<String, ResourceReference> jsFileMap) {
        this.ec = ec;
        this.jsFileMap = jsFileMap;
    }

    private ThreadLocal<NashornScriptEngine> engineHolder = new ThreadLocal<NashornScriptEngine>() {
        @Override
        protected NashornScriptEngine initialValue() {
            NashornScriptEngine nashornScriptEngine = (NashornScriptEngine) new ScriptEngineManager().getEngineByName("nashorn");
            try {
                for (ResourceReference rr : jsFileMap.values()){
                    if (rr == null) continue;
                    ec.getLogger().info("jsFileMap: " + rr.getFileName());
                }

                for (Map.Entry<String, ResourceReference> entry : jsFileMap.entrySet()){
                    if (entry.getValue() == null) continue;
                    ec.getLogger().info("Evaluating " + entry.getKey());
                    Object o = nashornScriptEngine.eval(new InputStreamReader(entry.getValue().openStream()));
                }

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

            // Object html = engineHolder.get().invokeFunction("renderServer")
            ScriptObjectMirror promise = (ScriptObjectMirror) engineHolder.get().invokeFunction("renderServer");
            Consumer<Object> fnResolve = (object) -> {
                html = object;
                System.out.println("------ from fnResolve");
                System.out.println(String.valueOf(html));
            };

            System.out.println(fnResolve);

            Consumer<Object> fnReject = (object) -> {
                error = object;
                System.out.println("------ from fnReject");
                System.out.println(String.valueOf(error));
            };
            promise.callMember("then", fnResolve, fnReject);

//            engineHolder.get().invokeFunction("saveValue", "one");
//            engineHolder.get().eval("saveValue('two')");

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
