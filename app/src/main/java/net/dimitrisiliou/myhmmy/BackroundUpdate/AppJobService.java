package net.dimitrisiliou.myhmmy.BackroundUpdate;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;

import net.dimitrisiliou.myhmmy.MainActivity;
import net.dimitrisiliou.myhmmy.R;
import net.dimitrisiliou.myhmmy.database.DataBaseHelper;
import net.dimitrisiliou.myhmmy.news.NewsModel;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;


public class AppJobService extends JobService {

    int notificationID = 0;
    TaskStackBuilder stackBuilder;
    Intent notificationIntent = new Intent(Intent.ACTION_VIEW);
    PendingIntent resultPendingIntent;
    NotificationCompat.Builder builder;
    NotificationManagerCompat notificationManager;
    private ArrayList<HashMap<String, String>> resultItems = new ArrayList<>();
    private static String mFeedUrl = "https://www.ece.uop.gr/announcement/feed/";
    NewsModel newsModel;
    JobScheduler jobScheduler;
    private static final String TAG = "teeeeeeeeeeeeeeeeeeesting";

    private boolean jobCancelled = false;
    @Override
    public boolean onStartJob(JobParameters params) {
        //turn on the notifications
        DataBaseHelper check = new DataBaseHelper(this);
        String notification = check.getSettings("NEWS_NOTIFICATION");
        if (notification.equals("yes")){
            jobCancelled = false;
        } else if (notification.equals("no")){
            jobCancelled = true;
        }
        Log.d(TAG, "Rss started");
        createNotificationChannel();
        doBackgroundWork(params);
        return true;
    }
    private void doBackgroundWork(final JobParameters params) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                // do something here
                jobScheduler = (JobScheduler)getSystemService(JOB_SCHEDULER_SERVICE);
                checkForNewAnnouncements();
                if (jobCancelled) {
                    return;
                }
                Log.d(TAG, "Rss finished");
                jobFinished(params, false);
            }
        }).start();
    }
    @Override
    public boolean onStopJob(JobParameters params) {
        //turn off the notifications
        Log.d(TAG, "Rss cancelled");
        jobCancelled = true;
        return true;
    }

    private void checkForNewAnnouncements(){

        DataBaseHelper dbHelper = new DataBaseHelper(getApplicationContext());
        getDataFromWeb(); // updates the articles but NOT the counter in the database
        SystemClock.sleep(10000);
        ArrayList<HashMap<String, String>> newsArray = dbHelper.getAllRss(); //gets all the new articles from the database
        int newsCounter = newsArray.size(); // gets the size of that array
        int previousNewsCounter = Integer.parseInt(dbHelper.getSettings("NEWS_COUNTER")); //gets the counter from the last time the user launched the app
        Log.d("newsCounter", String.valueOf(newsCounter));
        Log.d("previousNewsCounter", String.valueOf(previousNewsCounter));

        if (  newsCounter > previousNewsCounter) {
            int newNumber =   (newsCounter - previousNewsCounter) - 1 ;
            Log.d("newNumber", String.valueOf(newNumber));
            do{
                Intent intent = new Intent(this, MainActivity.class);
                PendingIntent pIntent = PendingIntent.getActivity(this, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT);
                Log.d("inside DO-WHILE", String.valueOf(newNumber));
                notificationIntent.setData(Uri.parse((newsArray.get(newNumber).get("link"))));
                stackBuilder = TaskStackBuilder.create(this);
                stackBuilder.addNextIntentWithParentStack(notificationIntent);
                resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
                builder = new NotificationCompat.Builder(this,"newNumber")
                        .setSmallIcon(R.drawable.ic_rss)
                        .setContentTitle("Νέα Ανακοίνωση")
                        .setContentText( newsArray.get(newNumber).get("title"))
                        .setGroup("newNumber1")
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(pIntent);
                notificationManager = NotificationManagerCompat.from(this);
                notificationManager.notify(notificationID,builder.build());
                notificationID++;
                newNumber--;
            } while (newNumber  >= 0);

        }
        dbHelper.changeSetting("NEWS_COUNTER", String.valueOf(newsArray.size())); //UPDATES THE COUNTER AFTER NOTIFYING THE USER
        Log.d(TAG, "Rss scannning");
    }

    private void createNotificationChannel()  {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name ="News Channel";
            String description = "Channel for student notification";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("newNumber", name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    class GetRssClass extends AsyncTask<Void, Void, ArrayList<HashMap<String, String>>>  {
        String mUrl;

        public GetRssClass(String url) {
            this.mUrl = url;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected ArrayList<HashMap<String, String>> doInBackground(Void... voids) {
            ArrayList<HashMap<String, String>> result = new ArrayList<>();
            try {
                URL url = new URL(mUrl);

                //------ trust all websites with ssl CA--------------------------
                try {
                    HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier(){
                        public boolean verify(String hostname, SSLSession session) {
                            return true;
                        }});
                    SSLContext context = SSLContext.getInstance("TLS");
                    context.init(null, new X509TrustManager[]{new X509TrustManager(){
                        public void checkClientTrusted(X509Certificate[] chain,
                                                       String authType) throws CertificateException {}
                        public void checkServerTrusted(X509Certificate[] chain,
                                                       String authType) throws CertificateException {}
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }}}, new SecureRandom());
                    HttpsURLConnection.setDefaultSSLSocketFactory(
                            context.getSocketFactory());
                } catch (Exception e) { // should never happen
                    e.printStackTrace();
                }

                //------------------------------------------------

                HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setDoInput( true );
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = connection.getInputStream();
                    result = parseXML(inputStream);
                } else {
                    return null;
                }
            } catch (Exception e) {
                Log.d("Exception", e.getMessage());
                return null;
            }
            return result;
        }


        @Override
        protected void onPostExecute(ArrayList<HashMap<String, String>> result) {
            super.onPostExecute(result);
        }

        private ArrayList<HashMap<String, String>> parseXML(InputStream inputStream)
                throws ParserConfigurationException, IOException, SAXException, ParseException {

            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(inputStream);
            Element element = document.getDocumentElement();

            NodeList itemlist = element.getElementsByTagName("item");
            NodeList itemChildren;

            Node currentItem;
            Node currentChild;

            ArrayList<HashMap<String, String>> items = new ArrayList<>();
            HashMap<String, String> currentMap;

            for (int i = 0; i < itemlist.getLength(); i++) {

                currentItem = itemlist.item(i);
                itemChildren = currentItem.getChildNodes();

                currentMap = new HashMap<>();

                for (int j = 0; j < itemChildren.getLength(); j++) {

                    currentChild = itemChildren.item(j);

                    if (currentChild.getNodeName().equalsIgnoreCase("title")) {
                        // Log.d("Title", String.valueOf(currentChild.getTextContent()));
                        currentMap.put("title", currentChild.getTextContent());
                    }

                    if (currentChild.getNodeName().equalsIgnoreCase("content:encoded")) {
                        // Log.d("description", String.valueOf(currentChild.getTextContent()));
                        currentMap.put("content:encoded", currentChild.getTextContent());
                    }

                    if (currentChild.getNodeName().equalsIgnoreCase("pubDate")) {
                        // Log.d("Title", String.valueOf(currentChild.getTextContent()));
                        currentMap.put("pubDate", currentChild.getTextContent());
                    }

                    if (currentChild.getNodeName().equalsIgnoreCase("link")) {
                        currentMap.put("link", currentChild.getTextContent());
                    }

                }
                if (!currentMap.isEmpty()) {
                    items.add(currentMap);
                    String link = (String)currentMap.get("link");
                    String title = (String)currentMap.get("title");
                    String pubDate = (String)currentMap.get("pubDate");
                    String content = (String)currentMap.get("content:encoded");

                    SimpleDateFormat inputPattern = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");
                    SimpleDateFormat outputPattern = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                    Date date = inputPattern.parse(pubDate);
                    String strDate = outputPattern.format(date);

                    DataBaseHelper dataBaseHelper = new DataBaseHelper(getApplicationContext());
                    newsModel = new NewsModel(link, title, strDate, content, "-1");
                    dataBaseHelper.addOne(newsModel);
                    dataBaseHelper.addOne(newsModel);

                }
            }
            return items;
        }
    }
    public void getDataFromWeb() {
        GetRssClass fetchRss = new GetRssClass(mFeedUrl);
        fetchRss.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}

