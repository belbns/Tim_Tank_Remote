package com.example.timtankremote;

import java.util.HashMap;

public class SampleGattAttributes {
    public static final String FFE0_SERVICE_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb";
    public static final String FFE1_CHAR_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb";

    private static HashMap<String, String> attributes = new HashMap();

    static {
        //attributes.put(UUID_BATTERY_LEVEL_UUID, "Battery Level");
        //attributes.put(UUID_BATTERY_SERVICE, "Battery Service");
        attributes.put(FFE0_SERVICE_UUID, "FFE0 Service");
        attributes.put(FFE1_CHAR_UUID, "FFE1 Message");
    }

    public static String lookup(String uuid) {
        String name = attributes.get(uuid);
        return name;
    }

}
