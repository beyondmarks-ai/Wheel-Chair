package org.wheelchair.blink;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** A small daily agenda. All schedules are entered by the user or caregiver. */
public final class CareActivity extends Activity {
    private LinearLayout root;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private CareRecording recording;
    private java.io.File exportFile;
    private final SoundPreview soundPreview=new SoundPreview();
    private final Runnable refresh=new Runnable(){public void run(){render();handler.postDelayed(this,5000);}};
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(LinearLayout box,String value,int size){return AppUi.label(box,value,size,size>=18?AppUi.INK:AppUi.MUTED,size>=18);}
    private Button button(LinearLayout box,String label,Runnable action){return AppUi.button(box,label,false,action);}
    private LinearLayout card(){return AppUi.card(root);}
    private static String time(long at){return at>0?DateTimeFormatter.ofPattern("EEE d MMM, HH:mm").format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())):"No upcoming time";}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);root=AppUi.screen(this,"Care plan","",2);CareAlerts.channels(this);
    }
    @Override protected void onResume(){super.onResume();CareScheduler.restore(this);handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);soundPreview.close();super.onPause();}
    private String rendered="";
    private void render(){
        var store=CareStore.get(this);var pending=store.pending();var plans=store.plans();
        String signature=plans.toString()+pending.toString()+CareScheduler.permitted(this)+getSystemService(NotificationManager.class).areNotificationsEnabled()+LocalDate.now();
        if(signature.equals(rendered))return;rendered=signature;
        root.removeAllViews();AppUi.header(root,"Care plan",LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")));
        var scheduled=AppUi.card(this);AppUi.metric(scheduled,"Scheduled","active reminders",AppUi.TEAL).setText(""+plans.size());
        var attention=AppUi.card(this);AppUi.metric(attention,"Needs attention","awaiting acknowledgement",AppUi.INK).setText(""+pending.size());AppUi.grid(root,scheduled,attention);
        AppUi.button(root,"+ Add a reminder",true,()->edit(null));
        if(!CareScheduler.permitted(this))button(root,"Enable exact reminder times",()->startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()))));
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)button(root,"Allow reminder notifications",()->requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},81));
        else if(!getSystemService(NotificationManager.class).areNotificationsEnabled())button(root,"Enable notifications",()->startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName())));
        if(!pending.isEmpty())AppUi.section(root,"Needs attention",null);
        for(var o:pending){
            LinearLayout box=card();text(box,o.title(),20);
            text(box,o.escalated()?"Not acknowledged":o.state().equals("SNOOZED")?"Snoozed until "+time(o.notifyAt()):"Due "+time(o.due()),13);
            var done=AppUi.column(this);var snooze=AppUi.column(this);
            AppUi.button(done,o.kind().equals("Medicine")?"Taken":"Done",true,()->action(o.id(),"DONE"));
            button(snooze,"Snooze 10 min",()->action(o.id(),"SNOOZE_ACTION"));AppUi.grid(box,done,snooze);
            var more=AppUi.disclosure(box,"More options");text(more,o.speaker(),13);button(more,"Skip reminder",()->action(o.id(),"SKIPPED"));
        }
        AppUi.section(root,"Your routine","Tap a reminder to edit its time or voice");
        if(plans.isEmpty()){var empty=card();text(empty,"Build your daily routine",19);text(empty,"Add meals, care activities and appointments at times you choose.",14);}
        java.util.List<android.view.View> tiles=new ArrayList<>();
        for(var plan:plans){
            var box=AppUi.card(this);AppUi.label(box,String.format(Locale.getDefault(),"%02d:%02d",plan.hour(),plan.minute()),27,AppUi.TEAL,true);
            text(box,plan.title(),18);text(box,repeatLabel(plan),12);text(box,CareSound.from(plan.voiceKey()).label,12);
            box.setClickable(true);box.setFocusable(true);box.setContentDescription("Edit "+plan.title()+" at "+plan.hour()+":"+plan.minute());box.setOnClickListener(v->edit(plan));tiles.add(box);
        }
        AppUi.grid(root,tiles.toArray(new android.view.View[0]));
        button(root,"Care history & report",this::history);
        var setup=AppUi.disclosure(root,"Voices & setup");
        button(setup,"Export SD voice pack",this::exportPack);button(setup,"Sounds & reminder setup",this::setup);
        button(setup,"Notification & guardian settings",()->AppUi.open(this,AlertsActivity.class));
    }
    private static String repeatLabel(CareStore.Plan p){
        if(p.once()!=null&&!p.once().isEmpty())return "Once on "+p.once();if(p.days()==127)return "Every day";
        StringBuilder s=new StringBuilder();for(int n=0;n<7;n++)if((p.days()&(1<<n))!=0)s.append(new String[]{"Mon","Tue","Wed","Thu","Fri","Sat","Sun"}[n]).append(' ');return s.toString().trim();
    }
    private void action(String id,String value){sendBroadcast(new Intent(this,CareReceiver.class).setAction(value).putExtra("id",id));handler.postDelayed(this::render,400);}
    private void edit(CareStore.Plan p){
        handler.removeCallbacks(refresh);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(8),dp(18),dp(8));ScrollView scroll=new ScrollView(this);scroll.addView(box);
        EditText title=new EditText(this);title.setHint("What should the speaker say?");title.setText(p==null?"":p.title());title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});box.addView(title);
        Spinner kind=new Spinner(this);String[] kinds={"Activity","Medicine","Meal","Appointment"};kind.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,kinds));if(p!=null)kind.setSelection(Arrays.asList(kinds).indexOf(p.kind()));box.addView(kind);
        text(box,"Choose the spoken message",16);
        Spinner voice=new Spinner(this);voice.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,Arrays.stream(CareSound.values()).map(s->s.label).toArray(String[]::new)));
        voice.setSelection(p==null?0:CareSound.from(p.voiceKey()).ordinal());box.addView(voice);


        button(box,"Preview selected natural voice on phone",()->{
            CareSound selected=CareSound.values()[voice.getSelectedItemPosition()];
            if(selected==CareSound.CUSTOM)Toast.makeText(this,"Your own words use Android speech. Save and export the custom recording below.",Toast.LENGTH_LONG).show();
            else if(!soundPreview.play(this,selected.resource))Toast.makeText(this,"Could not play preview. Check media volume.",Toast.LENGTH_LONG).show();
        });

        LocalTime now=LocalTime.now().plusMinutes(5);int[] chosen={p==null?now.getHour():p.hour(),p==null?now.getMinute():p.minute()};
        Button when=button(box,String.format(Locale.US,"Time: %02d:%02d",chosen[0],chosen[1]),()->{});
        when.setOnClickListener(v->new TimePickerDialog(this,(picker,h,m)->{chosen[0]=h;chosen[1]=m;when.setText(String.format(Locale.US,"Time: %02d:%02d",h,m));},chosen[0],chosen[1],true).show());
        CheckBox once=new CheckBox(this);once.setText("One date only");once.setChecked(p!=null&&p.once()!=null&&!p.once().isEmpty());box.addView(once);
        LocalDate[] date={once.isChecked()?LocalDate.parse(p.once()):LocalDate.now()};Button dateButton=button(box,"Date: "+date[0],()->{});
        dateButton.setOnClickListener(v->new DatePickerDialog(this,(view,y,m,d)->{date[0]=LocalDate.of(y,m+1,d);dateButton.setText("Date: "+date[0]);},date[0].getYear(),date[0].getMonthValue()-1,date[0].getDayOfMonth()).show());
        LinearLayout daysBox=new LinearLayout(this);daysBox.setOrientation(LinearLayout.VERTICAL);box.addView(daysBox);CheckBox[] days=new CheckBox[7];
        for(int n=0;n<7;n++){days[n]=new CheckBox(this);days[n].setText(new String[]{"Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"}[n]);days[n].setChecked(p==null||(p.days()&(1<<n))!=0);}
        AppUi.grid(daysBox,days);
        Runnable visibility=()->{dateButton.setVisibility(once.isChecked()?android.view.View.VISIBLE:android.view.View.GONE);daysBox.setVisibility(once.isChecked()?android.view.View.GONE:android.view.View.VISIBLE);};visibility.run();once.setOnCheckedChangeListener((v,on)->visibility.run());
        var extra=AppUi.disclosure(box,"Speaker & guardian options");
        text(extra,"Mark unacknowledged after this many minutes (5–120)",14);EditText grace=new EditText(this);grace.setInputType(2);grace.setText(""+(p==null?10:p.grace()));extra.addView(grace);
        CheckBox guardian=new CheckBox(this);guardian.setText("Guardian alert if skipped or not acknowledged");guardian.setChecked(p!=null&&p.guardian());extra.addView(guardian);text(extra,"Also enable care alerts and a recipient in Guardian settings.",13);
        CheckBox prepared=new CheckBox(this);prepared.setText("Matching recording has been copied to the DFPlayer SD card");prepared.setChecked(p!=null&&p.audioReady());prepared.setEnabled(p!=null&&p.track()>0);extra.addView(prepared);
        String[] autoTitle={""};
        voice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent){}
            public void onItemSelected(AdapterView<?> parent,android.view.View view,int position,long id){
                CareSound selected=CareSound.values()[position];boolean same=p!=null&&selected.key.equals(p.voiceKey());
                prepared.setEnabled(selected!=CareSound.CUSTOM||p!=null&&p.track()>0);
                prepared.setChecked(same&&p.audioReady());
                if(p==null&&selected!=CareSound.CUSTOM){
                    String entered=title.getText().toString().trim();
                    if(entered.isEmpty()||entered.equals(autoTitle[0])){title.setText(selected.label);autoTitle[0]=selected.label;}
                    kind.setSelection(Arrays.asList(kinds).indexOf(selected.reminderKind()));
                }
            }
        });
        if(p!=null&&p.track()>0){text(extra,"Recording: /mp3/"+String.format(Locale.US,"%04d",p.track())+(CareSound.from(p.voiceKey())==CareSound.CUSTOM?".wav":".mp3")+". Save voice or text changes before exporting; changes reset SD readiness.",13);button(extra,"Export spoken recording for SD card",()->export(p));}
        else text(extra,"Save first to export a spoken recording. Until copied to the SD card, the phone speaks the reminder.",13);
        text(extra,"Editing or deleting cancels outstanding reminders for this activity. Precise times require alarm permission; keep the phone powered on. Wired announcements require monitoring and Bluetooth.",13);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(p==null?"Add reminder":"Edit reminder").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",null).setNeutralButton(p==null?"":"Delete",null).create();
        dialog.setOnDismissListener(v->{handler.removeCallbacks(refresh);handler.post(refresh);});dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{
            int mask=0;for(int n=0;n<7;n++)if(days[n].isChecked())mask|=1<<n;
            String dateValue=once.isChecked()?date[0].toString():"";
            if(CareTime.next(System.currentTimeMillis(),ZoneId.systemDefault(),chosen[0],chosen[1],once.isChecked()?0:mask,dateValue)<0)throw new IllegalArgumentException("Choose a future time and at least one repeat day");
            String name=title.getText().toString().trim();int delay=Integer.parseInt(grace.getText().toString());if(name.isEmpty()||delay<5||delay>120)throw new IllegalArgumentException("Enter a title and 5–120 minutes");
            synchronized(CareScheduler.class){var store=CareStore.get(this);if(p!=null){CareScheduler.cancelPlan(this,p.id());for(var old:store.pending())if(old.plan()==p.id())CareVoiceService.cancel(old.id());}
                long id=store.save(p==null?0:p.id(),name,kind.getSelectedItem().toString(),chosen[0],chosen[1],once.isChecked()?0:mask,dateValue,prepared.isChecked(),delay,guardian.isChecked(),CareSound.values()[voice.getSelectedItemPosition()].key);CareScheduler.plan(this,store.getPlan(id));}
            dialog.dismiss();
        }catch(Exception e){Toast.makeText(this,e.getMessage()==null?"Check the reminder details":e.getMessage(),Toast.LENGTH_LONG).show();}});
        if(p!=null)dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Delete this reminder?").setMessage("History is kept. Future announcements and pending actions for this reminder are cancelled.").setNegativeButton("Keep",null).setPositiveButton("Delete",(d,w)->{synchronized(CareScheduler.class){CareScheduler.cancelPlan(this,p.id());CareStore.get(this).disable(p.id());}dialog.dismiss();}).show());
    }
    private void history(){
        handler.removeCallbacks(refresh);
        long today=LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        StringBuilder report=new StringBuilder("Blink Assist care report — "+LocalDate.now()+"\n\n");var list=CareStore.get(this).recent(today);int done=0,unacknowledged=0;
        for(var o:list){if(o.state().equals("DONE"))done++;if(o.escalated())unacknowledged++;report.append(time(o.due())).append(" · ").append(o.title()).append("\n").append(o.state().equals("DONE")&&o.kind().equals("Medicine")?"TAKEN (user reported)":o.state()).append(o.escalated()?" · acknowledgement deadline passed":"").append("\n").append(o.speaker()).append("\n\n");}
        report.append("Today: ").append(done).append(" done, ").append(unacknowledged).append(" passed their acknowledgement deadline.\n");
        report.append(HealthStore.get(this).dailySummary(today,System.currentTimeMillis()));
        LinearLayout box=AppUi.column(this);box.setPadding(dp(16),dp(8),dp(16),dp(8));ScrollView scroll=new ScrollView(this);scroll.addView(box);
        var doneCard=AppUi.card(this);AppUi.metric(doneCard,"Completed","today · user reported",AppUi.TEAL).setText(""+done);
        var lateCard=AppUi.card(this);AppUi.metric(lateCard,"Unacknowledged","today · deadline passed",AppUi.RED).setText(""+unacknowledged);AppUi.grid(box,doneCard,lateCard);
        var history=CareStore.get(this).recent(today-7*86400000L);String day="";
        if(history.isEmpty())text(box,"Your completed and missed reminders will appear here.",15);
        for(var o:history.subList(0,Math.min(60,history.size()))){
            var date=Instant.ofEpochMilli(o.due()).atZone(ZoneId.systemDefault());String current=DateTimeFormatter.ofPattern("EEE, d MMM").format(date);
            if(!current.equals(day)){AppUi.section(box,current,null);day=current;}
            var item=AppUi.card(box);AppUi.label(item,DateTimeFormatter.ofPattern("HH:mm").format(date)+"  ·  "+o.title(),17,AppUi.INK,true);
            AppUi.label(item,o.state().equals("DONE")?(o.kind().equals("Medicine")?"Taken · reported":"Completed"):o.state().toLowerCase(Locale.ROOT).replace('_',' '),14,o.state().equals("DONE")?AppUi.TEAL:AppUi.MUTED,true);
            var details=AppUi.disclosure(item,"Playback details");text(details,o.speaker(),13);
        }
        button(box,"Share today's report",()->startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,report.toString()),"Share care report")));
        var dialog=new AlertDialog.Builder(this).setTitle("Care timeline").setView(scroll).setPositiveButton("Close",null).create();
        dialog.setOnDismissListener(v->{handler.removeCallbacks(refresh);handler.post(refresh);});dialog.show();
    }
    private void setup(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(8),dp(16),dp(8));
        text(box,"Custom sounds are included for care, unacknowledged care, emergency and movement. Android volume, silent settings and channel settings still apply.",15);
        CheckBox motion=new CheckBox(this);motion.setText("Movement sound on this phone (at most once per 30 seconds)");var prefs=getSharedPreferences("settings",0);motion.setChecked(prefs.getBoolean("careMotionSound",true));motion.setOnCheckedChangeListener((v,on)->prefs.edit().putBoolean("careMotionSound",on).apply());box.addView(motion);
        button(box,"Notification sound settings",()->startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName())));
        button(box,"Phone speech settings",()->{try{startActivity(new Intent("com.android.settings.TTS_SETTINGS"));}catch(Exception e){Toast.makeText(this,"Open Android Settings and search Text-to-speech",Toast.LENGTH_LONG).show();}});
        text(box,"For the wired speaker: save a reminder, export its recording, copy it into mp3 on the existing DFPlayer card, then select the matching-recording checkbox. Existing emergency files stay in place. New reminders need the care controller firmware. The phone speaks when a wired request cannot be started.",14);
        text(box,"Allow Alarms & reminders and notifications. On Vivo, allow background activity/autostart for Blink Assist. Power-off or Force stop prevents reminders. Delays over two minutes are recorded without late playback or a replayed guardian SMS.",14);
        ScrollView scroll=new ScrollView(this);scroll.addView(box);new AlertDialog.Builder(this).setTitle("Sounds & setup").setView(scroll).setPositiveButton("Close",null).show();
    }
    private void export(CareStore.Plan plan){
        CareSound sound=CareSound.from(plan.voiceKey());
        if(sound!=CareSound.CUSTOM){
            try{
                exportFile=new java.io.File(getCacheDir(),"care-selected.mp3");
                try(var in=getResources().openRawResource(sound.resource);var out=new java.io.FileOutputStream(exportFile)){copy(in,out);}
                startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/mpeg").putExtra(Intent.EXTRA_TITLE,String.format(Locale.US,"%04d.mp3",plan.track())),82);
            }catch(Exception e){Toast.makeText(this,"Could not export voice",Toast.LENGTH_LONG).show();}
            return;
        }
        if(recording!=null){Toast.makeText(this,"Preparing the recording…",Toast.LENGTH_SHORT).show();return;}
        Toast.makeText(this,"Preparing spoken recording…",Toast.LENGTH_SHORT).show();
        recording=new CareRecording(this,plan.title(),(file,error)->{
            recording=null;if(isFinishing()||isDestroyed())return;
            if(file==null){Toast.makeText(this,error,Toast.LENGTH_LONG).show();return;}
            exportFile=file;startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/wav").putExtra(Intent.EXTRA_TITLE,String.format(Locale.US,"%04d.wav",plan.track())),82);
        });
    }
    @Override public void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==83&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            try(var output=getContentResolver().openOutputStream(data.getData(),"wt");var zip=new java.util.zip.ZipOutputStream(output)){
                for(var sound:CareSound.values())if(sound!=CareSound.CUSTOM)packVoice(zip,sound.track,sound.resource);
                packVoice(zip,2999,R.raw.voice_obstacle_demo);
                Toast.makeText(this,"Saved ZIP. Extract its mp3 folder onto the DFPlayer card; keep existing files.",Toast.LENGTH_LONG).show();
            }catch(Exception e){Toast.makeText(this,"Could not export voice pack",Toast.LENGTH_LONG).show();}
        }
        if(request==82&&result==RESULT_OK&&data!=null&&data.getData()!=null&&exportFile!=null){
            try(var out=getContentResolver().openOutputStream(data.getData(),"wt");var in=new java.io.FileInputStream(exportFile)){
                if(out==null)throw new java.io.IOException();byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
                Toast.makeText(this,"Exported. Copy into the SD card's mp3 folder, then enable the matching-recording checkbox.",Toast.LENGTH_LONG).show();
            }catch(Exception e){Toast.makeText(this,"Could not export recording",Toast.LENGTH_LONG).show();}
        }
    }
    private void exportPack(){startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE,"Blink-Assist-natural-voices.zip"),83);}
    private void packVoice(java.util.zip.ZipOutputStream zip,int track,int resource)throws java.io.IOException{
        zip.putNextEntry(new java.util.zip.ZipEntry(String.format(Locale.US,"mp3/%04d.mp3",track)));
        try(var in=getResources().openRawResource(resource)){copy(in,zip);}zip.closeEntry();
    }
    private static void copy(java.io.InputStream in,java.io.OutputStream out)throws java.io.IOException{
        byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
    }
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);soundPreview.close();if(recording!=null)recording.close();super.onDestroy();}
}
