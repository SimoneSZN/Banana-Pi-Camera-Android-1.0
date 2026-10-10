package it.szn.bananapicamera;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.view.*;
import android.widget.*;

import com.jcraft.jsch.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final String SERVICE="camera-cycle.service";
    private static final String PHOTOS="/mnt/camera_data/photos", VIDEOS="/mnt/camera_data/video";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private EditText hostField,userField,passField;
    private TextView status,counts,space,log;
    private Button startBtn,previewBtn,stopPreviewBtn;
    private ImageView previewImage;
    private volatile boolean previewOn=false;
    private volatile Session previewSession;
    private volatile ChannelExec previewChannel;
    private volatile boolean recording=false;
    private String host="192.168.4.1", user="simone", password="";
    private final Handler ui = new Handler(Looper.getMainLooper());

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private TextView text(String s, int sp){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setPadding(dp(4),dp(7),dp(4),dp(7));return t;}
    private Button button(String s, LinearLayout parent, View.OnClickListener click){Button b=new Button(this);b.setText(s);parent.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(click);return b;}
    private EditText field(String hint,String value,LinearLayout parent, boolean secret){EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setText(value);if(secret)e.setInputType(129);parent.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    private void note(String s){ui.post(()->{status.setText(s); log.append("\n"+s);});}
    private void message(String s){ui.post(()->new AlertDialog.Builder(this).setMessage(s).setPositiveButton("OK",null).show());}
    private boolean credentials(){host=hostField.getText().toString().trim();user=userField.getText().toString().trim();password=passField.getText().toString(); if(host.isEmpty()||user.isEmpty()||password.isEmpty()){message("Inserisci IP, utente e password SSH.");return false;}return true;}
    @Override public void onCreate(Bundle b){super.onCreate(b); getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(16),dp(14),dp(16),dp(20));scroll.addView(root);setContentView(scroll);
        TextView title=text("BANANA PI CAMERA",23);title.setTextColor(Color.rgb(14,91,163));root.addView(title);
        root.addView(text("Connetti il telefono al Wi-Fi BANANAPI_CAMERA dalle impostazioni Android.",13));
        hostField=field("IP Banana Pi","192.168.4.1",root,false);
        userField=field("Utente SSH","simone",root,false);
        passField=field("Password SSH","",root,true);
        button("VERIFICA CONNESSIONE / AGGIORNA",root,v->submit(()->refresh()));
        status=text("Non connessa",16);root.addView(status);
        root.addView(text("REGISTRAZIONE",19));
        startBtn=button("▶ START CICLO",root,v->submit(()->service("start")));
        button("■ STOP CICLO",root,v->submit(()->service("stop")));
        root.addView(text("LIVE PREVIEW 640 × 480",19));
        previewImage=new ImageView(this);previewImage.setBackgroundColor(Color.rgb(24,38,53));previewImage.setScaleType(ImageView.ScaleType.FIT_CENTER);root.addView(previewImage,new LinearLayout.LayoutParams(-1,dp(230)));
        previewBtn=button("▶ LIVE PREVIEW",root,v->startPreview());
        stopPreviewBtn=button("■ STOP PREVIEW",root,v->stopPreview());stopPreviewBtn.setEnabled(false);
        root.addView(text("MICROSD",19));counts=text("Foto: —    Video: —",15);space=text("Spazio: —",15);root.addView(counts);root.addView(space);
        button("SCARICA FOTO",root,v->submit(()->download(PHOTOS)));
        button("SCARICA VIDEO",root,v->submit(()->download(VIDEOS)));
        root.addView(text("I file vengono salvati in Download/BananaPiCamera sul telefono.",12));
        log=text("Registro attività",12);root.addView(log);
    }
    private void submit(Runnable job){if(!credentials())return; worker.execute(()->{try{job.run();}catch(Exception ex){note("Errore: "+ex.getMessage());}});}
    private Session connect() throws Exception {
        JSch jsch=new JSch();File known=new File(getFilesDir(),"known_hosts");if(!known.exists())known.createNewFile();jsch.setKnownHosts(known.getAbsolutePath());
        Session s=jsch.getSession(user,host,22);s.setPassword(password);s.setConfig("StrictHostKeyChecking","ask");
        s.setUserInfo(new UserInfo(){
            public String getPassphrase(){return null;}public String getPassword(){return password;}
            public boolean promptPassphrase(String x){return false;}public boolean promptPassword(String x){return false;}
            public void showMessage(String x){note(x);}
            public boolean promptYesNo(String q){CountDownLatch latch=new CountDownLatch(1);final boolean[] yes={false};ui.post(()->new AlertDialog.Builder(MainActivity.this).setTitle("Identità server SSH").setMessage(q+"\n\nAccetta solo se stai collegandoti alla tua Banana Pi.").setPositiveButton("Accetta",(d,w)->{yes[0]=true;latch.countDown();}).setNegativeButton("Annulla",(d,w)->latch.countDown()).setOnCancelListener(d->latch.countDown()).show());try{latch.await(60,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return yes[0];}
        });
        s.connect(9000);return s;
    }
    private String exec(Session s,String cmd) throws Exception{ChannelExec c=(ChannelExec)s.openChannel("exec");c.setCommand(cmd);ByteArrayOutputStream out=new ByteArrayOutputStream();c.setOutputStream(out);c.setErrStream(out);c.connect(8000);long until=System.currentTimeMillis()+15000;while(!c.isClosed() && System.currentTimeMillis()<until)Thread.sleep(40);if(!c.isClosed()){c.disconnect();throw new IOException("Timeout comando remoto");}int code=c.getExitStatus();c.disconnect();String value=out.toString("UTF-8").trim();if(code!=0 && !cmd.startsWith("systemctl is-active"))throw new IOException(value+" (exit "+code+")");return value;}
    private String getState(Session s) throws Exception{return exec(s,"systemctl is-active "+SERVICE).trim();}
    private void refresh() throws Exception{Session s=connect();try{String state=getState(s);recording="active".equals(state);note("Ciclo registrazione: "+state.toUpperCase(Locale.ROOT));String filecounts=exec(s,"find "+PHOTOS+" -maxdepth 1 -type f | wc -l; find "+VIDEOS+" -maxdepth 1 -type f | wc -l");String[] v=filecounts.split("\\s+");if(v.length>=2)ui.post(()->counts.setText("Foto: "+v[0]+"    Video: "+v[1]));String disk=exec(s,"df -h /mnt/camera_data | tail -1");ui.post(()->space.setText("MicroSD: "+disk));ui.post(()->startBtn.setEnabled(!previewOn));}finally{s.disconnect();}}
    private void service(String action) throws Exception{
        if(action.equals("start") && previewOn) {note("Chiudi prima la preview.");return;}
        Session s=connect();try{
            if(action.equals("start") && previewOn){note("Preview attiva: avvio negato.");return;}
            ChannelExec c=(ChannelExec)s.openChannel("exec");
            c.setCommand("sudo -S -p '' systemctl "+action+" "+SERVICE);
            c.setInputStream(new ByteArrayInputStream((password+"\n").getBytes(StandardCharsets.UTF_8)));
            ByteArrayOutputStream output=new ByteArrayOutputStream();c.setOutputStream(output);c.setErrStream(output);
            c.connect(8000);long deadline=System.currentTimeMillis()+18000;
            while(!c.isClosed()&&System.currentTimeMillis()<deadline)Thread.sleep(60);
            if(!c.isClosed()){c.disconnect();throw new IOException("Timeout sudo/systemctl");}
            int code=c.getExitStatus();c.disconnect();
            if(code!=0)throw new IOException("Comando systemctl non riuscito ("+code+"): "+output.toString("UTF-8"));
        }finally{s.disconnect();}
        refresh();
    }
    private void startPreview(){if(previewOn)return;if(!credentials())return;previewOn=true;previewBtn.setEnabled(false);stopPreviewBtn.setEnabled(true);startBtn.setEnabled(false);worker.execute(()->previewLoop());}
    private void stopPreview(){previewOn=false;ChannelExec c=previewChannel;if(c!=null)c.disconnect();Session s=previewSession;if(s!=null)s.disconnect();ui.post(()->{previewBtn.setEnabled(true);stopPreviewBtn.setEnabled(false);startBtn.setEnabled(true);});}
    private void previewLoop(){int frames=0;Session s=null;ChannelExec c=null;try{
        s=connect();previewSession=s;String state=getState(s);if(!state.equals("inactive")&&!state.equals("failed"))throw new IOException("Preview non disponibile: ferma prima il ciclo (stato "+state+").");
        c=(ChannelExec)s.openChannel("exec");previewChannel=c;c.setCommand("ffmpeg -hide_banner -loglevel error -nostdin -f v4l2 -input_format mjpeg -video_size 640x480 -framerate 10 -i /dev/video1 -c:v copy -f image2pipe pipe:1");
        InputStream in=c.getInputStream();c.connect(8000);note("Preview avviata: 640 × 480 a 10 fps");ByteArrayOutputStream jpeg=new ByteArrayOutputStream(150000);int prev=-1,cur;boolean inside=false;
        while(previewOn && (cur=in.read())!=-1){if(!inside){if(prev==255&&cur==216){inside=true;jpeg.reset();jpeg.write(255);jpeg.write(216);}}else{jpeg.write(cur);if(prev==255&&cur==217){byte[] buf=jpeg.toByteArray();Bitmap bmp=BitmapFactory.decodeByteArray(buf,0,buf.length);if(bmp!=null)ui.post(()->{if(previewOn)previewImage.setImageBitmap(bmp);});frames++;inside=false;}if(jpeg.size()>4_000_000){inside=false;jpeg.reset();}}prev=cur;}
        if(frames==0&&previewOn)throw new IOException("Nessun frame: controlla ffmpeg e formato MJPEG 640x480 su /dev/video1.");
    }catch(Exception ex){if(previewOn)note("Preview: "+ex.getMessage());}finally{if(c!=null)c.disconnect();if(s!=null)s.disconnect();previewChannel=null;previewSession=null;previewOn=false;ui.post(()->{previewBtn.setEnabled(true);stopPreviewBtn.setEnabled(false);startBtn.setEnabled(true);});}}
    private void download(String remote) throws Exception{if(previewOn){note("Ferma prima la preview per scaricare i file.");return;}Session s=connect();ChannelSftp sftp=null;try{
        sftp=(ChannelSftp)s.openChannel("sftp");sftp.connect(8000);java.util.Vector<ChannelSftp.LsEntry> entries=sftp.ls(remote);int downloaded=0,skipped=0;
        for(ChannelSftp.LsEntry entry:entries){if(entry.getAttrs().isDir())continue;String filename=entry.getFilename();if(filename.equals(".")||filename.equals(".."))continue;
            String safe=filename.replaceAll("[^A-Za-z0-9._-]","_");String sub=remote.equals(PHOTOS)?"photos":"video";
            Uri dest=findExisting(sub,safe,entry.getAttrs().getSize());if(dest!=null){skipped++;continue;}
            ContentValues values=new ContentValues();values.put(MediaStore.Downloads.DISPLAY_NAME,safe);values.put(MediaStore.Downloads.MIME_TYPE,sub.equals("photos")?"image/jpeg":"application/octet-stream");values.put(MediaStore.Downloads.RELATIVE_PATH,"Download/BananaPiCamera/"+sub);values.put(MediaStore.Downloads.IS_PENDING,1);
            Uri uri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("Impossibile creare "+safe);
            try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Output non disponibile");sftp.get(remote+"/"+filename,out);}catch(Exception err){getContentResolver().delete(uri,null,null);throw err;}
            ContentValues done=new ContentValues();done.put(MediaStore.Downloads.IS_PENDING,0);getContentResolver().update(uri,done,null,null);downloaded++;if(downloaded%5==0)note("Scaricati "+downloaded+" file da "+sub);
        }
        note("Download completato: "+downloaded+" nuovi, "+skipped+" già presenti.");
    }finally{if(sftp!=null)sftp.disconnect();s.disconnect();}}
    private Uri findExisting(String sub,String filename,long size){android.database.Cursor cursor=null;try{
        String path="Download/BananaPiCamera/"+sub+"/";cursor=getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,new String[]{MediaStore.Downloads._ID,MediaStore.Downloads.SIZE},MediaStore.Downloads.DISPLAY_NAME+"=? AND "+MediaStore.Downloads.RELATIVE_PATH+"=?",new String[]{filename,path},null);
        if(cursor!=null&&cursor.moveToFirst()){do{if(cursor.getLong(1)==size)return android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cursor.getLong(0));}while(cursor.moveToNext());}
    }catch(Exception ignored){}finally{if(cursor!=null)cursor.close();}return null;}
    @Override protected void onDestroy(){stopPreview();worker.shutdownNow();super.onDestroy();}
}
