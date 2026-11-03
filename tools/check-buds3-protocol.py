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
for name in ['activities.devicesettings.DeviceSettingsPreferenceConst','service.devices.galaxy_buds.GalaxyBudsProtocol','service.devices.galaxy_buds.Buds3LightDiagnostics','service.devices.galaxy_buds.GalaxyBudsDeviceSupport']:
    path=Path((base+'.'+name).replace('.','/')+'.java'); dest=tmp/path;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy(main/path,dest)
put('android.content.SharedPreferences','public interface SharedPreferences { boolean getBoolean(String k,boolean d); String getString(String k,String d); int getInt(String k,int d); }')
put('android.os.Bundle',"""public class Bundle {
 private final java.util.Map<String,Object> v=new java.util.HashMap<>(); public Bundle(){} public Bundle(Bundle b){v.putAll(b.v);}
 public void putString(String k,String x){v.put(k,x);} public void putInt(String k,int x){v.put(k,x);} public void putBoolean(String k,boolean x){v.put(k,x);}
 public boolean containsKey(String k){return v.containsKey(k);} public String getString(String k,String d){return (String)v.getOrDefault(k,d);}
 public int getInt(String k,int d){return (Integer)v.getOrDefault(k,d);} public boolean getBoolean(String k,boolean d){return (Boolean)v.getOrDefault(k,d);}
}""")
put('android.os.Looper','public class Looper { public static Looper getMainLooper(){return new Looper();} }')
put('android.os.Handler',"""public class Handler {
 public static final java.util.List<Runnable> tasks=new java.util.ArrayList<>(); public static final java.util.List<Long> delays=new java.util.ArrayList<>();
 public Handler(Looper l){} public void postDelayed(Runnable r,long d){tasks.add(r);delays.add(d);} public void removeCallbacksAndMessages(Object o){tasks.clear();delays.clear();}
 public static void next(){ Runnable r=tasks.remove(0); delays.remove(0);r.run(); } public static void all(){while(!tasks.isEmpty())next();}
}""")
put('androidx.annotation.Nullable','public @interface Nullable {}')
put('org.slf4j.Logger','public interface Logger { default void debug(String s,Object... a) {} default void warn(String s,Object... a) {} default void info(String s,Object... a) {} default void error(String s,Object... a) {} }')
put('org.slf4j.LoggerFactory','public class LoggerFactory { public static Logger getLogger(Class<?> c){ return new Logger(){}; } }')
put(base+'.model.DeviceType','public enum DeviceType { GALAXY_BUDS, GALAXY_BUDS_LIVE, GALAXY_BUDS_PRO, GALAXY_BUDS2, GALAXY_BUDS2_PRO, GALAXY_BUDS3_PRO }')
put(base+'.model.BatteryState','public enum BatteryState { BATTERY_NORMAL, UNKNOWN }')
put(base+'.impl.GBDevice','public class GBDevice { public static final int BATTERY_UNKNOWN=-1; public enum State { INITIALIZED } private boolean connected=true; public boolean isConnected(){return connected;} public void setConnected(boolean b){connected=b;} private final '+base+'.model.DeviceType type; public GBDevice('+base+'.model.DeviceType t){type=t;} public '+base+'.model.DeviceType getType(){return type;} public String getAddress(){return "test";} }')
put(base+'.deviceevents.GBDeviceEvent','public class GBDeviceEvent {}')
put(base+'.deviceevents.GBDeviceEventVersionInfo','public class GBDeviceEventVersionInfo extends GBDeviceEvent {}')
put(base+'.deviceevents.GBDeviceEventBatteryInfo','public class GBDeviceEventBatteryInfo extends GBDeviceEvent { public int batteryIndex,level; public '+base+'.model.BatteryState state; }')
put(base+'.deviceevents.GBDeviceEventUpdatePreferences','public class GBDeviceEventUpdatePreferences extends GBDeviceEvent { public final java.util.Map<String,Object> preferences=new java.util.HashMap<>(); public GBDeviceEventUpdatePreferences(String k,Object v){preferences.put(k,v);} public GBDeviceEventUpdatePreferences withPreference(String k,Object v){preferences.put(k,v);return this;} }')
put(base+'.GBApplication','public class GBApplication { public static final java.util.Map<String,String> values=new java.util.HashMap<>(); public static android.content.SharedPreferences getDeviceSpecificSharedPrefs(String a){return new android.content.SharedPreferences(){ public boolean getBoolean(String k,boolean d){return d;} public int getInt(String k,int d){return d;} public String getString(String k,String d){return values.getOrDefault(k,d);} };} }')
put(base+'.service.serial.GBDeviceProtocol','public class GBDeviceProtocol { public static final int RESET_FLAGS_FACTORY_RESET=1; private final '+base+'.impl.GBDevice device; protected GBDeviceProtocol('+base+'.impl.GBDevice d){device=d;} protected '+base+'.impl.GBDevice getDevice(){return device;} public '+base+'.deviceevents.GBDeviceEvent[] decodeResponse(byte[] b){return null;} public byte[] encodeSendConfiguration(String s){return null;} public byte[] encodeFindDevice(boolean b){return null;} public byte[] encodeReset(int b){return null;} public byte[] encodeTestNewFunction(android.os.Bundle b){return null;} }')
put(base+'.service.btbr.TransactionBuilder',"""public class TransactionBuilder {
 public static final java.util.List<byte[]> sent=new java.util.ArrayList<>(); private byte[] data;
 public TransactionBuilder write(byte... d){data=d.clone();return this;} public void queue(){sent.add(data);} public TransactionBuilder setDeviceState("""+base+""".impl.GBDevice.State s){return this;}
}""")
put(base+'.service.serial.AbstractHeadphoneSerialDeviceSupportV2',"""public abstract class AbstractHeadphoneSerialDeviceSupportV2<T extends GBDeviceProtocol> {
 protected T mDeviceProtocol; private """+base+""".impl.GBDevice device;
 public void setup("""+base+""".impl.GBDevice d){device=d;mDeviceProtocol=createDeviceProtocol();}
 protected abstract T createDeviceProtocol(); protected """+base+""".impl.GBDevice getDevice(){return device;}
 protected java.util.UUID getSupportedService(){return null;}
 protected """+base+""".service.btbr.TransactionBuilder initializeDevice("""+base+""".service.btbr.TransactionBuilder b){return b;}
 public """+base+""".service.btbr.TransactionBuilder createTransactionBuilder(String s){return new """+base+""".service.btbr.TransactionBuilder();}
 public void onSendConfiguration(String c){} public void onTestNewFunction(android.os.Bundle b){}
 public void onSocketRead(byte[] b){mDeviceProtocol.decodeResponse(b);} public void disconnect(){device.setConnected(false);} public void dispose(){}
}""")
put(base+'.util.GB','public class GB { public static String hexdump(byte[] b){return java.util.HexFormat.of().formatHex(b);} }')
checks=(main/Path((base+'.util.CheckSums').replace('.','/')+'.java')).read_text()
start=checks.index('    private static int[] Crc16Tab ='); end=checks.index('    @Nullable',start)
put(base+'.util.CheckSums','public class CheckSums {\n'+checks[start:end]+'}')
put(base+'.service.devices.galaxy_buds.BladeLightsCheck','''
import java.util.*;
import android.os.Bundle;
import android.os.Handler;
import nodomain.freeyourgadget.gadgetbridge.service.btbr.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.*;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.*;
public class BladeLightsCheck {
 static int checks=0;
 static void check(boolean ok,String what){ checks++;if(!ok)throw new AssertionError(what); }
 static Bundle request(String action){Bundle b=new Bundle();b.putString(Buds3LightDiagnostics.EXPERIMENT,action);return b;}
 static void invalid(Runnable r,String label){boolean rejected=false;try{r.run();}catch(IllegalArgumentException e){rejected=true;}check(rejected,label);}
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
  for(int index:new int[]{0,1,2,4,5,6,7}){byte[] bad=on.clone();bad[index]^=1;p.resetLightStream();check(p.decodeResponse(bad).length==0,"reject corrupt byte "+index);}
  p.resetLightStream();
  check(p.decodeResponse(Arrays.copyOf(on,7)).length==0,"truncated frame");
  p.resetLightStream();
  check(p.decodeResponse(Arrays.copyOf(on,9)).length==1,"valid frame followed by padding");
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

  p.resetLightStream();
  for(int i=0;i<on.length;i++)check(p.decodeResponse(new byte[]{on[i]}).length==(i==7?1:0),"split frame byte "+i);
  byte[] combined=new byte[on.length+off.length];System.arraycopy(on,0,combined,0,on.length);System.arraycopy(off,0,combined,on.length,off.length);
  check(p.decodeResponse(combined).length==2,"combined frames");
  p.resetLightStream();byte[] broken=on.clone();broken[1]=100;p.decodeResponse(broken);
  check(p.decodeResponse(off).length==1,"resync corrupt length before valid frame");
  check(Arrays.equals(on,Buds3LightDiagnostics.frame(0x8e,new byte[]{1},0)),"debug encoder on fixture");
  check(Arrays.equals(off,Buds3LightDiagnostics.frame(0x8e,new byte[]{0},0)),"debug encoder off fixture");
  check(Buds3LightDiagnostics.parsePayload("").length==0,"empty payload");
  check(Arrays.equals(new byte[]{1,0},Buds3LightDiagnostics.parsePayload("01 00")),"hex payload");
  invalid(()->Buds3LightDiagnostics.parsePayload("0"),"odd hex rejected");
  invalid(()->Buds3LightDiagnostics.parsePayload("GG"),"invalid hex rejected");
  invalid(()->Buds3LightDiagnostics.parsePayload("00".repeat(33)),"oversized payload rejected");
  invalid(()->Buds3LightDiagnostics.frame(0x50,new byte[0],0),"reset opcode unavailable");
  invalid(()->Buds3LightDiagnostics.frame(0x8e,new byte[]{1},0x10),"fragment flag unavailable");
  invalid(()->Buds3LightDiagnostics.frame(0x90,new byte[1],0),"wrong touch settings size rejected");
  invalid(()->Buds3LightDiagnostics.frame(0x24,new byte[]{1},0),"firmware reads parameterless");
  var capture=Buds3LightDiagnostics.forDevice("capture");capture.clear();long before=capture.onReportCount();
  capture.setCapturing(false);capture.received(on);check(capture.onReportCount()==before+1,"ON reports still stop runs when capture paused");
  capture.clear();check(capture.onReportCount()==before+1,"ON counter monotonic across clear");
  capture.setCapturing(true);for(int i=0;i<1400;i++)capture.note("test "+i);
  check(!capture.report().contains("older entries dropped=0"),"ring buffer bounded");
  check(capture.report().length()<200000,"report size bounded");
  GBDevice d=new GBDevice(DeviceType.GALAXY_BUDS3_PRO);GalaxyBudsDeviceSupport support=new GalaxyBudsDeviceSupport();support.setup(d);
  Handler.tasks.clear();Handler.delays.clear();TransactionBuilder.sent.clear();
  Bundle b=request("custom");b.putString(Buds3LightDiagnostics.PAYLOAD,"01");b.putInt(Buds3LightDiagnostics.REPEAT,3);
  support.onTestNewFunction(b);b.putString(Buds3LightDiagnostics.PAYLOAD,"FF");
  check(Handler.tasks.size()==3,"three scheduled sends");Handler.all();
  check(TransactionBuilder.sent.size()==3,"three queued sends");
  for(byte[] sent:TransactionBuilder.sent)check(Arrays.equals(sent,on),"immutable payload request");
  TransactionBuilder.sent.clear();support.onTestNewFunction(request("on3"));Handler.next();support.onSocketRead(on);Handler.all();
  check(TransactionBuilder.sent.size()==1,"new ON report cancels remaining sends");
  support.onTestNewFunction(request("on3"));Runnable stale=Handler.tasks.get(0);support.onSendConfiguration(Buds3LightDiagnostics.CANCEL);stale.run();
  check(Handler.tasks.isEmpty() && TransactionBuilder.sent.size()==1,"cancel rejects stale callbacks");
  TransactionBuilder.sent.clear();Bundle continuous=request("on3");continuous.putBoolean(Buds3LightDiagnostics.STOP_ON,false);
  support.onTestNewFunction(continuous);Handler.next();support.onSocketRead(on);Handler.all();
  check(TransactionBuilder.sent.size()==3,"stop-on-report can be disabled");
  TransactionBuilder.sent.clear();support.onTestNewFunction(request("on3"));support.disconnect();Handler.all();
  check(TransactionBuilder.sent.isEmpty(),"disconnect cancels all pending frames");
  support.onTestNewFunction(request("custom"));check(Handler.tasks.isEmpty(),"no experiment while disconnected");d.setConnected(true);
  support.onTestNewFunction(request("firmware"));Handler.all();check(TransactionBuilder.sent.size()==2,"two firmware reads");
  check((TransactionBuilder.sent.get(0)[3]&255)==0x24 && (TransactionBuilder.sent.get(1)[3]&255)==0x28,"read opcodes");
  TransactionBuilder.sent.clear();support.onTestNewFunction(request("off_style_on"));Handler.all();
  check(TransactionBuilder.sent.size()==3,"style sequence length");
  check((TransactionBuilder.sent.get(0)[3]&255)==0x8e && (TransactionBuilder.sent.get(1)[3]&255)==0x90 && (TransactionBuilder.sent.get(2)[3]&255)==0x8e,"style sequence opcodes");
  TransactionBuilder.sent.clear();Bundle sweep=request("sweep_all");sweep.putInt(Buds3LightDiagnostics.DELAY,250);support.onTestNewFunction(sweep);
  check(Handler.tasks.size()==256,"full lighting parameter sweep");check(Handler.delays.get(1)==1000,"sweep minimum spacing");Handler.all();
  check(TransactionBuilder.sent.size()==256,"all sweep frames queued");
  for(int i=0;i<256;i++)check((TransactionBuilder.sent.get(i)[3]&255)==0x8e && (TransactionBuilder.sent.get(i)[4]&255)==i,"sweep stays on lighting opcode "+i);
  TransactionBuilder.sent.clear();Bundle invalid=request("custom");invalid.putString(Buds3LightDiagnostics.PAYLOAD,"GG");support.onTestNewFunction(invalid);Handler.all();
  check(TransactionBuilder.sent.isEmpty(),"invalid custom packet not queued");
  System.out.println("PASS: "+checks+" checks on actual protocol, capture, stream parser and experiment runner with Android boundary stubs.");
 }
}
''')
files=[str(p) for p in tmp.rglob('*.java')]
subprocess.run(['javac','-d',str(tmp/'classes'),*files],check=True)
subprocess.run(['java','-cp',str(tmp/'classes'),base+'.service.devices.galaxy_buds.BladeLightsCheck'],check=True)
print('Harness directory:',tmp)
