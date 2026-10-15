package org.wheelchair.blink;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.*;
import android.widget.*;

/** Shared visual language and adaptive layouts for the everyday screens. */
final class AppUi {
    static final int INK=0xff172c3d,MUTED=0xff5c7080,TEAL=0xff087d79,BG=0xfff3f6f8,
            LINE=0xffe1e8ed,SOFT=0xffe2f3ef,BLUE=0xffe9effc,PEACH=0xffffeee4,RED=0xffad293b;
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable shape(Context c,int color,int radius){var s=new GradientDrawable();s.setColor(color);s.setCornerRadius(dp(c,radius));return s;}
    static LinearLayout column(Context c){var l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static TextView text(Context c,String value,int size,int color,boolean bold){
        var t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("tnum");
        t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));t.setIncludeFontPadding(false);t.setPadding(0,dp(c,3),0,dp(c,5));return t;
    }
    static TextView label(LinearLayout parent,String value,int size,int color,boolean bold){var t=text(parent.getContext(),value,size,color,bold);parent.addView(t);return t;}
    static void gap(LinearLayout p,int height){p.addView(new View(p.getContext()),new LinearLayout.LayoutParams(1,dp(p.getContext(),height)));}
    static LinearLayout card(Context c){var p=column(c);int pad=dp(c,16);p.setPadding(pad,pad,pad,pad);p.setBackground(shape(c,Color.WHITE,20));return p;}
    static LinearLayout card(LinearLayout parent){var p=card(parent.getContext());var lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(parent.getContext(),12);parent.addView(p,lp);return p;}
    static void section(LinearLayout p,String title,String subtitle){gap(p,12);label(p,title,20,INK,true);if(subtitle!=null&&!subtitle.isEmpty())label(p,subtitle,13,MUTED,false);gap(p,7);}
    static Button button(LinearLayout p,String label,boolean primary,Runnable action){
        Context c=p.getContext();Button b=new Button(c);b.setAllCaps(false);b.setText(label);b.setTextSize(15);b.setTextColor(primary?Color.WHITE:TEAL);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x22087d79),shape(c,primary?TEAL:SOFT,14),null));
        b.setMinHeight(dp(c,50));b.setPadding(dp(c,12),dp(c,10),dp(c,12),dp(c,10));
        var lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(c,8);p.addView(b,lp);b.setOnClickListener(v->action.run());return b;
    }
    static void grid(LinearLayout parent,View... cells){
        Context c=parent.getContext();boolean single=c.getResources().getConfiguration().screenWidthDp<340||c.getResources().getConfiguration().fontScale>1.3f;
        for(int n=0;n<cells.length;){
            LinearLayout row=new LinearLayout(c);row.setBaselineAligned(false);
            var rowParams=new LinearLayout.LayoutParams(-1,-2);rowParams.bottomMargin=dp(c,12);parent.addView(row,rowParams);
            for(int j=0;j<(single?1:2)&&n<cells.length;j++,n++){
                var lp=new LinearLayout.LayoutParams(0,-1,1);if(j>0)lp.leftMargin=dp(c,12);row.addView(cells[n],lp);
            }
        }
    }
    static TextView metric(LinearLayout card,String title,String unit,int color){
        label(card,title,13,MUTED,false);var value=label(card,"—",28,color,true);label(card,unit,12,MUTED,false);return value;
    }
    static LinearLayout tile(Activity a,String icon,String title,String subtitle,int color,Class<?> destination){
        var p=card(a);p.setMinimumHeight(dp(a,138));
        p.addView(new Icon(a,icon,TEAL),new LinearLayout.LayoutParams(dp(a,30),dp(a,30)));gap(p,12);
        label(p,title,18,INK,true);label(p,subtitle,12,MUTED,false);p.setBackground(shape(a,color,20));
        p.setClickable(true);p.setFocusable(true);p.setContentDescription(title+". "+subtitle);p.setOnClickListener(v->open(a,destination));return p;
    }
    static void open(Activity a,Class<?> target){if(a.getClass()!=target)a.startActivity(new Intent(a,target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
    static LinearLayout screen(Activity a,String title,String subtitle,int tab){
        LinearLayout frame=column(a);frame.setBackgroundColor(BG);
        frame.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(0,i.getSystemWindowInsetTop(),0,i.getSystemWindowInsetBottom());return i;});
        // Force decor creation before configuring system bars (Vivo can otherwise
        // throw from PhoneWindow.getInsetsController during Activity.onCreate).
        a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);scroll.setClipToPadding(false);frame.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout root=column(a);root.setPadding(dp(a,20),dp(a,20),dp(a,20),dp(a,16));scroll.addView(root);
        header(root,title,subtitle);
        LinearLayout nav=new LinearLayout(a);nav.setBackgroundColor(Color.WHITE);nav.setPadding(dp(a,8),dp(a,8),dp(a,8),dp(a,8));frame.addView(nav);
        String[] labels={"Home","Health","Care","Alerts"},icons={"home","health","care","alerts"};
        Class<?>[] targets={HomeActivity.class,HealthActivity.class,CareActivity.class,AlertsActivity.class};
        for(int i=0;i<4;i++){
            int at=i;LinearLayout item=column(a);item.setGravity(Gravity.CENTER);item.setPadding(dp(a,4),dp(a,8),dp(a,4),dp(a,6));
            if(i==tab)item.setBackground(shape(a,SOFT,14));int tint=i==tab?TEAL:MUTED;
            item.addView(new Icon(a,icons[i],tint),new LinearLayout.LayoutParams(dp(a,23),dp(a,23)));
            var name=text(a,labels[i],12,tint,i==tab);name.setGravity(Gravity.CENTER);item.addView(name);
            item.setContentDescription(labels[i]+" tab");item.setClickable(true);item.setFocusable(true);item.setOnClickListener(v->open(a,targets[at]));nav.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        a.setContentView(frame);return root;
    }
    static TextView header(LinearLayout root,String title,String subtitle){
        var t=label(root,title,28,INK,true);if(subtitle!=null&&!subtitle.isEmpty())label(root,subtitle,14,MUTED,false);gap(root,20);return t;
    }
    static LinearLayout disclosure(LinearLayout p,String title){
        LinearLayout body=column(p.getContext());body.setVisibility(View.GONE);
        Button toggle=button(p,title+"  +",false,()->{});p.addView(body);
        toggle.setOnClickListener(v->{boolean show=body.getVisibility()!=View.VISIBLE;body.setVisibility(show?View.VISIBLE:View.GONE);toggle.setText(title+(show?"  −":"  +"));});return body;
    }
    static final class Icon extends View {
        final Paint p=new Paint(3);final String type;final int color;
        Icon(Context c,String type,int color){super(c);this.type=type;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/24f,getHeight()/24f);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            Path path=new Path();
            switch(type){
                case "home":path.moveTo(3,10);path.lineTo(12,3);path.lineTo(21,10);path.moveTo(5,9);path.lineTo(5,21);path.lineTo(10,21);path.lineTo(10,15);path.lineTo(14,15);path.lineTo(14,21);path.lineTo(19,21);path.lineTo(19,9);canvas.drawPath(path,p);break;
                case "health":path.moveTo(2,13);path.lineTo(6,13);path.lineTo(9,5);path.lineTo(13,20);path.lineTo(17,10);path.lineTo(20,13);path.lineTo(23,13);canvas.drawPath(path,p);break;
                case "care":canvas.drawRoundRect(3,5,21,22,3,3,p);canvas.drawLine(3,10,21,10,p);canvas.drawLine(8,2,8,7,p);canvas.drawLine(16,2,16,7,p);canvas.drawLine(7,15,10,15,p);canvas.drawLine(14,15,17,15,p);canvas.drawLine(7,18,10,18,p);break;
                case "alerts":path.moveTo(4,18);path.lineTo(6,15);path.lineTo(6,9);path.cubicTo(6,1,18,1,18,9);path.lineTo(18,15);path.lineTo(20,18);path.close();canvas.drawPath(path,p);canvas.drawArc(9,18,15,23,0,180,false,p);break;
                case "camera":canvas.drawRoundRect(2,6,17,20,3,3,p);path.moveTo(17,10);path.lineTo(23,7);path.lineTo(23,19);path.lineTo(17,16);canvas.drawPath(path,p);break;
                default:canvas.drawCircle(12,7,4,p);canvas.drawArc(3,13,21,28,180,180,false,p);break;
            }canvas.restore();
        }
    }
}
