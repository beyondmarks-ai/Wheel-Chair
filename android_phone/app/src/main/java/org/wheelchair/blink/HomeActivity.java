package org.wheelchair.blink;
import android.app.Activity;
import android.content.Intent;
import android.os.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class HomeActivity extends Activity {
    private TextView pulse,oxygen,connection,upcoming;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){
        var live=HealthState.latest;boolean fresh=HealthState.connected&&live!=null&&live.fresh(SystemClock.elapsedRealtime());
        var reading=fresh?live.reading():null;
        pulse.setText(number(reading==null?null:reading.bpm()));oxygen.setText(number(reading==null?null:reading.oxygen()));
        connection.setText(MonitorState.manualMode?"Speaker mode · Camera off":MonitorState.running?"Monitoring is active":"Ready when you are");
        var next=CareStore.get(HomeActivity.this).plans().stream().filter(p->p.next()>System.currentTimeMillis()).min(java.util.Comparator.comparingLong(CareStore.Plan::next));
        upcoming.setText(next.isPresent()?DateTimeFormatter.ofPattern("EEE, h:mm a").format(Instant.ofEpochMilli(next.get().next()).atZone(ZoneId.systemDefault()))+"\n"+next.get().title():"No upcoming reminders\nAdd your daily routine in Care");
        handler.postDelayed(this,2000);
    }};
    private String number(Double value){return value==null?"—":String.format(Locale.US,"%.0f",value);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);LinearLayout root=AppUi.screen(this,"Blink Assist","Your daily care, in one place",0);
        // The familiar title remains the hidden entry to manual controls.
        ((TextView)root.getChildAt(0)).setOnLongClickListener(v->{startActivity(new Intent(this,AdminActivity.class));return true;});
        var hero=AppUi.card(root);hero.setBackground(AppUi.shape(this,AppUi.INK,22));
        AppUi.label(hero,"LIVE ASSIST",11,0xffa1ddd2,true);connection=AppUi.label(hero,"Ready when you are",23,android.graphics.Color.WHITE,true);
        AppUi.label(hero,"Camera, gestures and speaker controls",13,0xffc7d8e2,false);
        AppUi.button(hero,"Open live monitoring",true,()->AppUi.open(this,MainActivity.class));
        AppUi.section(root,"At a glance","Current readings from your controller");
        var heart=AppUi.card(this);pulse=AppUi.metric(heart,"Heart rate","bpm",AppUi.TEAL);
        var o2=AppUi.card(this);oxygen=AppUi.metric(o2,"Oxygen estimate","% SpO₂",AppUi.INK);AppUi.grid(root,heart,o2);
        AppUi.section(root,"Your care space",null);
        AppUi.grid(root,AppUi.tile(this,"health","Health","Charts & timeline",AppUi.SOFT,HealthActivity.class),
                AppUi.tile(this,"care","Care plan","Times & voice reminders",AppUi.BLUE,CareActivity.class),
                AppUi.tile(this,"alerts","Alerts","Notifications & sounds",AppUi.PEACH,AlertsActivity.class),
                AppUi.tile(this,"guardian","Guardian","Contacts & SMS delivery",0xffeeeafa,GuardianActivity.class));
        var next=AppUi.card(root);AppUi.label(next,"UP NEXT",11,AppUi.TEAL,true);upcoming=AppUi.label(next,"",18,AppUi.INK,true);
        AppUi.button(next,"View care plan",false,()->AppUi.open(this,CareActivity.class));
        AppUi.label(root,"Prototype health estimates · Missing readings show —",12,AppUi.MUTED,false);
    }
    @Override protected void onResume(){super.onResume();CareScheduler.restore(this);handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
}
