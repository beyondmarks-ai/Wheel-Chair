package org.wheelchair.blink;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

public final class HealthActivity extends Activity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean loading=new AtomicBoolean();
    private boolean visible;
    private TextView status,summary;
    private final TextView[] readings=new TextView[6],statistics=new TextView[3];
    private LinearLayout timeline;
    private String timelineSignature="";
    private HealthChart chart;
    private Spinner metric,period;
    private final long[] periods={3600000,86400000,7L*86400000,30L*86400000,0};
    private final Runnable refresh=new Runnable(){public void run(){if(!visible)return;load();ui.postDelayed(this,2000);}};
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private TextView text(LinearLayout root,String value,int size){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(0xff142d3d);t.setPadding(0,dp(8),0,dp(8));root.addView(t);return t;
    }
    private Button button(LinearLayout root,String title){Button b=new Button(this);b.setText(title);b.setAllCaps(false);root.addView(b,new LinearLayout.LayoutParams(-1,dp(52)));return b;}
    private Spinner selector(LinearLayout root,String[] labels){
        Spinner s=new Spinner(this);var a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);root.addView(s,new LinearLayout.LayoutParams(-1,dp(52)));return s;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);LinearLayout root=AppUi.screen(this,"Health & history","Readings, trends and your care timeline",1);
        status=AppUi.label(root,"Waiting for controller",13,AppUi.TEAL,true);AppUi.gap(root,10);
        String[] names={"Heart rate","Oxygen estimate","Room temperature","Humidity","Obstacle distance","Movement"};
        String[] units={"bpm","% SpO₂","°C · room","% relative humidity","cm · sensor reading","controller state"};
        View[] cards=new View[6];
        for(int n=0;n<6;n++){var card=AppUi.card(this);readings[n]=AppUi.metric(card,names[n],units[n],n==0?AppUi.TEAL:AppUi.INK);cards[n]=card;}
        AppUi.grid(root,cards);
        AppUi.section(root,"Reading trends","Choose a measurement and time range");
        var graph=AppUi.card(root);
        var metricBox=AppUi.column(this);AppUi.label(metricBox,"MEASUREMENT",10,AppUi.MUTED,true);
        metric=selector(metricBox,new String[]{"Heart rate","Oxygen estimate","Room temperature","Humidity","Obstacle distance"});
        var rangeBox=AppUi.column(this);AppUi.label(rangeBox,"TIME RANGE",10,AppUi.MUTED,true);
        period=selector(rangeBox,new String[]{"Last hour","24 hours","7 days","30 days","All history"});period.setSelection(1);AppUi.grid(graph,metricBox,rangeBox);
        chart=new HealthChart(this);graph.addView(chart,new LinearLayout.LayoutParams(-1,dp(220)));
        summary=AppUi.label(graph,"",12,AppUi.MUTED,false);
        var stats=new LinearLayout(this);graph.addView(stats);
        for(int n=0;n<3;n++){var cell=AppUi.column(this);AppUi.label(cell,new String[]{"LOW","AVERAGE","HIGH"}[n],10,AppUi.MUTED,true);statistics[n]=AppUi.label(cell,"—",19,AppUi.INK,true);stats.addView(cell,new LinearLayout.LayoutParams(0,-2,1));}
        var details=AppUi.disclosure(root,"Reading details & export");
        AppUi.label(details,"Prototype estimates. Oxygen is estimated; temperature is room temperature. Gaps represent missing readings. Longer periods show averages.",13,AppUi.MUTED,false);
        AppUi.button(details,"Export readings as CSV",false,()->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .setType("text/csv").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"wheelchair-readings.csv"),30));
        AppUi.section(root,"Activity timeline","Latest 40 events in this range · Tap for details");
        timeline=AppUi.column(this);root.addView(timeline);
        AdapterView.OnItemSelectedListener select=new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){load();}
        };metric.setOnItemSelectedListener(select);period.setOnItemSelectedListener(select);
    }
    private static String value(Double n,String unit){return n==null?"--":String.format(Locale.US,"%.1f %s",n,unit);}
    private void load(){
        if(!visible||!loading.compareAndSet(false,true))return;
        int selectedMetric=metric.getSelectedItemPosition(),selectedPeriod=period.getSelectedItemPosition();
        long end=System.currentTimeMillis();
        worker.execute(()->{
            try{
                var store=HealthStore.get(this);
                long since=selectedPeriod==4?store.firstReading(end-3600000):end-periods[selectedPeriod];
                var points=store.points(since,selectedMetric);var entries=store.history();
                // Previous APKs already stored blink timestamps; keep that history visible.
                try(EventStore old=new EventStore(this);var c=old.getReadableDatabase().rawQuery("SELECT payload FROM events ORDER BY rowid DESC LIMIT 40",null)){
                    while(c.moveToNext())try{var e=new JSONObject(c.getString(0));entries.add(new HealthStore.Entry(Instant.parse(e.getString("observedAt")).toEpochMilli(),
                            e.optString("eventType").equals("blink_sos_test")?"Three-blink alert detected":"Connection test"));}catch(Exception ignored){}
                }
                entries.sort(Comparator.comparingLong(HealthStore.Entry::at).reversed());
                var filtered=entries.stream().filter(e->(selectedPeriod==4||e.at()>=since)&&e.at()<=end).limit(40).collect(java.util.stream.Collectors.toList());
                var time=DateTimeFormatter.ofPattern("h:mm:ss a");
                ui.post(()->{
                    if(!visible||isFinishing())return;
                    if(selectedMetric!=metric.getSelectedItemPosition()||selectedPeriod!=period.getSelectedItemPosition())return;
                    var snapshot=HealthState.latest;boolean fresh=HealthState.connected&&snapshot!=null&&snapshot.fresh(SystemClock.elapsedRealtime());
                    status.setText(fresh?"● Live · Updated "+time.format(Instant.ofEpochMilli(snapshot.wallTime()).atZone(ZoneId.systemDefault())):
                            "○ No live reading"+(snapshot==null?"":" · Last received "+time.format(Instant.ofEpochMilli(snapshot.wallTime()).atZone(ZoneId.systemDefault()))));
                    var r=fresh?snapshot.reading():null;
                    Double[] values={r==null?null:r.bpm(),r==null?null:r.oxygen(),r==null?null:r.roomC(),r==null?null:r.humidity(),r==null?null:r.distance()};
                    for(int n=0;n<5;n++)readings[n].setText(values[n]==null?"—":String.format(Locale.US,n==2?"%.1f":"%.0f",values[n]));
                    readings[5].setText(r==null||r.moving()==null?"—":r.moving()?"Moving":"Still");
                    chart.data(points,since,end);renderTimeline(filtered);
                    var valid=points.stream().filter(p->p.value()!=null).mapToDouble(p->p.value()).summaryStatistics();
                    summary.setText(valid.getCount()==0?"No saved readings in this range":valid.getCount()+(valid.getCount()==1?" displayed sample · ":" displayed samples · ")+new String[]{"bpm","% SpO₂","°C room","% humidity","cm"}[selectedMetric]);
                    double[] measures={valid.getMin(),valid.getAverage(),valid.getMax()};
                    for(int n=0;n<3;n++)statistics[n].setText(valid.getCount()==0?"—":String.format(Locale.US,"%.1f",measures[n]));
                });
            }catch(Exception error){ui.post(()->{if(visible)status.setText("Could not load history. Try reopening this page.");});}
            finally{loading.set(false);}
        });
    }
    private void renderTimeline(java.util.List<HealthStore.Entry> entries){
        String signature=entries.toString();if(signature.equals(timelineSignature))return;timelineSignature=signature;timeline.removeAllViews();
        if(entries.isEmpty()){var empty=AppUi.card(timeline);AppUi.label(empty,"Your timeline starts here",18,AppUi.INK,true);AppUi.label(empty,"Recorded events will appear with their time and details.",14,AppUi.MUTED,false);return;}
        String lastDay="";
        for(var entry:entries){
            var at=Instant.ofEpochMilli(entry.at()).atZone(ZoneId.systemDefault());String day=at.toLocalDate().equals(LocalDate.now())?"Today":DateTimeFormatter.ofPattern("EEE, d MMM").format(at);
            if(!day.equals(lastDay)){AppUi.label(timeline,day,15,AppUi.MUTED,true);lastDay=day;}
            LinearLayout row=new LinearLayout(this);row.setBaselineAligned(false);var lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(10);timeline.addView(row,lp);
            var clock=AppUi.text(this,DateTimeFormatter.ofPattern("HH:mm").format(at)+"\n│",13,AppUi.TEAL,true);clock.setPadding(0,dp(14),dp(10),0);row.addView(clock,new LinearLayout.LayoutParams(dp(60),-1));
            var card=AppUi.card(this);row.addView(card,new LinearLayout.LayoutParams(0,-2,1));
            String raw=entry.text();int colon=raw.indexOf(": ");String title=colon>0?raw.substring(0,colon):raw;
            AppUi.label(card,title,16,AppUi.INK,true);AppUi.label(card,"Recorded at "+DateTimeFormatter.ofPattern("HH:mm:ss").format(at),12,AppUi.MUTED,false);
            var detail=AppUi.label(card,colon>0?raw.substring(colon+2):raw,14,AppUi.MUTED,false);detail.setVisibility(View.GONE);
            card.setClickable(true);card.setFocusable(true);card.setContentDescription(title+" at "+DateTimeFormatter.ofPattern("HH:mm").format(at)+". Show details");
            card.setOnClickListener(v->detail.setVisibility(detail.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==30&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            var uri=data.getData();worker.execute(()->{
                String message;
                try(var output=getContentResolver().openOutputStream(uri,"wt")){
                    if(output==null)throw new java.io.IOException();HealthStore.get(this).exportCsv(output);message="Readings exported";
                }catch(Exception error){message="Export failed";}
                String resultText=message;ui.post(()->Toast.makeText(this,resultText,Toast.LENGTH_LONG).show());
            });
        }
    }
    @Override protected void onStart(){super.onStart();visible=true;ui.post(refresh);}
    @Override protected void onStop(){visible=false;ui.removeCallbacks(refresh);super.onStop();}
    @Override protected void onDestroy(){worker.shutdown();super.onDestroy();}
}
