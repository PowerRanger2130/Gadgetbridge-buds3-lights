#!/usr/bin/env python3
"""Local protocol checks, using Android stubs without building an APK."""
from pathlib import Path
import tempfile, subprocess, re, shutil
repo=Path(__file__).resolve().parents[1]
main=repo/'app/src/main/java'
base='nodomain.freeyourgadget.gadgetbridge'
tmp=Path(tempfile.mkdtemp(prefix='buds3-java-check-'))
def put(name,body):
    path=tmp/(name.replace('.','/')+'.java'); path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text('package '+name.rsplit('.',1)[0]+';\n'+body)
for name in ['activities.devicesettings.DeviceSettingsPreferenceConst','service.devices.galaxy_buds.GalaxyBudsProtocol']:
    path=Path((base+'.'+name).replace('.','/')+'.java'); dest=tmp/path;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy(main/path,dest)
put('android.content.SharedPreferences','public interface SharedPreferences { boolean getBoolean(String k,boolean d); String getString(String k,String d); int getInt(String k,int d); }')
put('android.os.Bundle','public class Bundle {}')
put('androidx.annotation.Nullable','public @interface Nullable {}')
put('org.slf4j.Logger','public interface Logger { default void debug(String s,Object... a) {} default void warn(String s,Object... a) {} default void info(String s,Object... a) {} default void error(String s,Object... a) {} }')
put('org.slf4j.LoggerFactory','public class LoggerFactory { public static Logger getLogger(Class<?> c){ return new Logger(){}; } }')
put(base+'.model.DeviceType','public enum DeviceType { GALAXY_BUDS, GALAXY_BUDS_LIVE, GALAXY_BUDS_PRO, GALAXY_BUDS2, GALAXY_BUDS2_PRO, GALAXY_BUDS3_PRO }')
put(base+'.model.BatteryState','public enum BatteryState { BATTERY_NORMAL, UNKNOWN }')
put(base+'.impl.GBDevice','public class GBDevice { public static final int BATTERY_UNKNOWN=-1; private final '+base+'.model.DeviceType type; public GBDevice('+base+'.model.DeviceType t){type=t;} public '+base+'.model.DeviceType getType(){return type;} public String getAddress(){return "test";} }')
put(base+'.deviceevents.GBDeviceEvent','public class GBDeviceEvent {}')
put(base+'.deviceevents.GBDeviceEventVersionInfo','public class GBDeviceEventVersionInfo extends GBDeviceEvent {}')
put(base+'.deviceevents.GBDeviceEventBatteryInfo','public class GBDeviceEventBatteryInfo extends GBDeviceEvent { public int batteryIndex,level; public '+base+'.model.BatteryState state; }')
put(base+'.deviceevents.GBDeviceEventUpdatePreferences','public class GBDeviceEventUpdatePreferences extends GBDeviceEvent { public final java.util.Map<String,Object> preferences=new java.util.HashMap<>(); public GBDeviceEventUpdatePreferences(String k,Object v){preferences.put(k,v);} public GBDeviceEventUpdatePreferences withPreference(String k,Object v){preferences.put(k,v);return this;} }')
put(base+'.GBApplication','public class GBApplication { public static final java.util.Map<String,String> values=new java.util.HashMap<>(); public static android.content.SharedPreferences getDeviceSpecificSharedPrefs(String a){return new android.content.SharedPreferences(){ public boolean getBoolean(String k,boolean d){return d;} public int getInt(String k,int d){return d;} public String getString(String k,String d){return values.getOrDefault(k,d);} };} }')
put(base+'.service.serial.GBDeviceProtocol','public class GBDeviceProtocol { public static final int RESET_FLAGS_FACTORY_RESET=1; private final '+base+'.impl.GBDevice device; protected GBDeviceProtocol('+base+'.impl.GBDevice d){device=d;} protected '+base+'.impl.GBDevice getDevice(){return device;} public '+base+'.deviceevents.GBDeviceEvent[] decodeResponse(byte[] b){return null;} public byte[] encodeSendConfiguration(String s){return null;} public byte[] encodeFindDevice(boolean b){return null;} public byte[] encodeReset(int b){return null;} public byte[] encodeTestNewFunction(android.os.Bundle b){return null;} }')
put(base+'.util.GB','public class GB { public static String hexdump(byte[] b){return java.util.HexFormat.of().formatHex(b);} }')
checks=(main/Path((base+'.util.CheckSums').replace('.','/')+'.java')).read_text()
start=checks.index('    private static int[] Crc16Tab ='); end=checks.index('    @Nullable',start)
put(base+'.util.CheckSums','public class CheckSums {\n'+checks[start:end]+'}')
put(base+'.service.devices.galaxy_buds.BladeLightsCheck','''
import java.util.*;
import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.*;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.*;
public class BladeLightsCheck {
 static int checks=0;
 static void check(boolean ok,String what){ checks++;if(!ok)throw new AssertionError(what); }
 public static void main(String[] args){
  GalaxyBudsProtocol p=new GalaxyBudsProtocol(new GBDevice(DeviceType.GALAXY_BUDS3_PRO));
  byte[] off=HexFormat.of().parseHex("fd04008e009738dd"), on=HexFormat.of().parseHex("fd04008e01b628dd");
  check(Arrays.equals(off,p.encodeSendConfiguration(PREF_GALAXY_BUDS_3_PRO_LIGHTS_OFF)),"off fixture");
  check(Arrays.equals(on,p.encodeSendConfiguration(PREF_GALAXY_BUDS_3_PRO_LIGHTS_ON)),"on fixture");
  check(GBApplication.values.isEmpty(),"commands do not persist requested state");
  for(DeviceType type:DeviceType.values())if(type!=DeviceType.GALAXY_BUDS3_PRO){
   GalaxyBudsProtocol other=new GalaxyBudsProtocol(new GBDevice(type));
   check(other.encodeSendConfiguration(PREF_GALAXY_BUDS_3_PRO_LIGHTS_ON)==null,"on model guard "+type);
   check(other.encodeSendConfiguration(PREF_GALAXY_BUDS_3_PRO_LIGHTS_OFF)==null,"off model guard "+type);
  }
  for(int state=0;state<4;state++){
   byte[] frame=p.encodeMessage((byte)0x8e,(byte)state);
   GBDeviceEvent[] events=p.decodeResponse(frame);
   check(events.length==1 && events[0] instanceof GBDeviceEventUpdatePreferences,"status event "+state);
   var prefs=((GBDeviceEventUpdatePreferences)events[0]).preferences;
   check(prefs.get(PREF_GALAXY_BUDS_3_PRO_LIGHTS_STATE).equals(state),"status value "+state);
   check((Long)prefs.get(PREF_GALAXY_BUDS_3_PRO_LIGHTS_STATE_TIME)>0,"status timestamp "+state);
  }
  for(int index:new int[]{0,1,2,4,5,6,7}){byte[] bad=on.clone();bad[index]^=1;check(p.decodeResponse(bad).length==0,"reject corrupt byte "+index);}
  check(p.decodeResponse(Arrays.copyOf(on,7)).length==0,"truncated frame");
  check(p.decodeResponse(Arrays.copyOf(on,9)).length==0,"oversized frame");
  byte[] ack=on.clone();ack[2]=0x20;check(p.decodeResponse(ack).length==0,"ACK must not prove LED state");
  check(p.decodeResponse(p.encodeMessage((byte)0x8e,(byte)4)).length==0,"unknown state");
  int[] styles={2,3,1};
  for(int ui=0;ui<3;ui++){
   GBApplication.values.put(PREF_GALAXY_BUDS_3_PRO_EARBUD_LIGHTS,Integer.toString(ui));
   byte[] frame=p.encodeSendConfiguration(PREF_GALAXY_BUDS_3_PRO_EARBUD_LIGHTS);
   check(frame.length==17 && (frame[3]&255)==0x90,"touch packet layout");
   check(frame[12]==styles[ui],"wire style mapping "+ui);
   check(frame[5]==1 && frame[6]==1 && frame[7]==1,"media controls preserved");
  }
  System.out.println("PASS: "+checks+" checks on actual GalaxyBudsProtocol and checksum source with Android boundary stubs.");
 }
}
''')
files=[str(p) for p in tmp.rglob('*.java')]
subprocess.run(['javac','-d',str(tmp/'classes'),*files],check=True)
subprocess.run(['java','-cp',str(tmp/'classes'),base+'.service.devices.galaxy_buds.BladeLightsCheck'],check=True)
print('Harness directory:',tmp)
