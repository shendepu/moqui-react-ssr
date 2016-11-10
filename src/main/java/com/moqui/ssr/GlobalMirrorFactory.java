package com.moqui.ssr;

import jdk.nashorn.api.scripting.NashornScriptEngine;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

import javax.script.ScriptContext;
import javax.script.SimpleScriptContext;

public class GlobalMirrorFactory extends BasePooledObjectFactory<ScriptContext> {
    private NashornScriptEngine nashornEngine;

    public GlobalMirrorFactory(NashornScriptEngine nashornEngine) {
        this.nashornEngine = nashornEngine;
    }

    @Override
    public ScriptContext create() throws Exception {
        ScriptContext sc = new SimpleScriptContext();
        sc.setBindings(nashornEngine.createBindings(), ScriptContext.ENGINE_SCOPE);
        sc.getBindings(ScriptContext.ENGINE_SCOPE).putAll(nashornEngine.getBindings(ScriptContext.ENGINE_SCOPE));

        return sc;
    }

    @Override
    public PooledObject<ScriptContext> wrap(ScriptContext obj) {
        return new DefaultPooledObject<>(obj);
    }
}
