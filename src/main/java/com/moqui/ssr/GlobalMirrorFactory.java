package com.moqui.ssr;

import jdk.nashorn.api.scripting.NashornScriptEngine;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import java.util.Map;

public class GlobalMirrorFactory extends BasePooledObjectFactory<ScriptContext> {
    private NashornScriptEngine nashornEngine;
    private Map<String, CompiledScript> compiledScriptMap;

    public GlobalMirrorFactory(NashornScriptEngine nashornEngine, Map<String, CompiledScript> compiledScriptMap) {
        this.nashornEngine = nashornEngine;
        this.compiledScriptMap = compiledScriptMap;
    }

    @Override
    public ScriptContext create() throws Exception {
        ScriptContext sc = new SimpleScriptContext();
        sc.setBindings(nashornEngine.createBindings(), ScriptContext.ENGINE_SCOPE);
        sc.getBindings(ScriptContext.ENGINE_SCOPE).putAll(nashornEngine.getBindings(ScriptContext.ENGINE_SCOPE));

        sc.setAttribute("__REQ_URL__", "/", ScriptContext.ENGINE_SCOPE);
        try {
            for (Map.Entry<String, CompiledScript> entry : compiledScriptMap.entrySet()) {
                entry.getValue().eval(sc);
            }
        } catch (ScriptException e) {
            throw new RuntimeException(e);
        }
        return sc;
    }

    @Override
    public PooledObject<ScriptContext> wrap(ScriptContext obj) {
        return new DefaultPooledObject<>(obj);
    }
}
