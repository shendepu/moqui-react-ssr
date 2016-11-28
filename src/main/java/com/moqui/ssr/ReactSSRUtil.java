package com.moqui.ssr;

import org.moqui.context.ExecutionContext;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReactSSRUtil {
    private static String reactSSRStaticPath = "component://moqui-react-ssr/screen/ReactSSRRoot/static";

    public static Map<String, Map<String, Object>> getStaticJavascript(ExecutionContext ec, String filename, boolean runOnce) {
        Map<String, Map<String, Object>> map = new HashMap<>(1);
        Map<String, Object> propertyMap = new HashMap<>(2);
        propertyMap.put("runOnce", runOnce);
        propertyMap.put("resourceReference", ec.getResource().getLocationReference(reactSSRStaticPath + "/" + filename + ".js"));
        map.put(filename, propertyMap);
        return map;
    }

    public static Map<String, Map<String, Object>> getStaticJavascripts(ExecutionContext ec, List<String> filenames, boolean runOnce) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>(filenames.size());
        for (String filename : filenames) {
            map.putAll(getStaticJavascript(ec, filename, runOnce));
        }
        return map;
    }


}
