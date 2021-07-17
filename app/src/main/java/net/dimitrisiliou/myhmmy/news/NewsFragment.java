package net.dimitrisiliou.myhmmy.news;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.util.Log;import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.dimitrisiliou.myhmmy.R;
import net.dimitrisiliou.myhmmy.database.DataBaseHelper;
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

public class NewsFragment extends Fragment {

    private RecyclerView mRecyclerView;
    private NewsAdapter mAdapter;
    private ArrayList<HashMap<String, String>> resultItems = new ArrayList<>();
    private static String mFeedUrl = "https://www.ece.uop.gr/announcement/feed/";
    NewsModel newsModel;
    DataBaseHelper dataBaseHelper;
    private ProgressDialog progressDialog;
    private boolean isLoading;

    public NewsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        View rootView =  inflater.inflate(R.layout.fragment_news, container, false);

        getDataFromWeb();
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mRecyclerView = getView().findViewById(R.id.recycler_view);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        dataBaseHelper = new DataBaseHelper(getContext());
        mAdapter = new NewsAdapter(getContext(), resultItems);
        mRecyclerView.setAdapter(mAdapter);
    }

    public class GetRssClass extends AsyncTask<Void, Void, ArrayList<HashMap<String, String>>> {
        String mUrl;

        public GetRssClass(String url) {
            this.mUrl = url;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showProgressDialog();
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
                connection.setConnectTimeout(10000);
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
            hideProgressDialog();
            if (result == null) {
                showToast("Please check your connection and try again.");
            }

                int before = resultItems.size();
                resultItems.clear();
                ArrayList<HashMap<String, String>> everyone = dataBaseHelper.getAllRss();
                dataBaseHelper.changeSetting("NEWS_COUNTER", String.valueOf(everyone.size())); //adds the complete number of items that exists in the database
                resultItems.addAll(everyone);
                mAdapter.notifyItemRangeInserted(before, everyone.size());
                mRecyclerView.invalidate();

        }

        private ArrayList<HashMap<String, String>> parseXML(InputStream inputStream)
                throws ParserConfigurationException, IOException, SAXException, ParseException  {

            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(inputStream);
            Element element = document.getDocumentElement();

            NodeList itemlist = element.getElementsByTagName("item");
            NodeList itemChildren;

            Node currentItem;
            Node currentChild;

            String link =null;
            String title = null;
            String pubDate = null;
            String content = null;
            String newDateFormat = null;


            ArrayList<HashMap<String, String>> items = new ArrayList<>(); //the one that returns at the end of the execution
            HashMap<String, String> currentMap; //supported hashmap

            for (int i = 0; i < itemlist.getLength(); i++) { //for each article

                currentItem = itemlist.item(i);
                itemChildren = currentItem.getChildNodes();
                currentMap = new HashMap<>();

                for (int j = 0; j < itemChildren.getLength(); j++) { //for each tag inside the article
                    currentChild = itemChildren.item(j);

                    if (currentChild.getNodeName().equalsIgnoreCase("title")) {
                        // Log.d("Title", String.valueOf(currentChild.getTextContent()));
                        currentMap.put("title", currentChild.getTextContent());
                        title = (String) currentChild.getTextContent();
                    }
                    if (currentChild.getNodeName().equalsIgnoreCase("content:encoded")) { //content for a future use
                        // Log.d("description", String.valueOf(currentChild.getTextContent()));
                        currentMap.put("content:encoded", currentChild.getTextContent());
                        content = (String) currentChild.getTextContent();
                    }
                    if (currentChild.getNodeName().equalsIgnoreCase("pubDate")) {
                        // Log.d("Title", String.valueOf(currentChild.getTextContent()));
                        currentMap.put("pubDate", currentChild.getTextContent());
                        pubDate = (String) currentChild.getTextContent();
                        @SuppressLint("SimpleDateFormat")
                        SimpleDateFormat inputPattern = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");
                        @SuppressLint("SimpleDateFormat")
                        SimpleDateFormat outputPattern = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                        Date date = inputPattern.parse(pubDate);
                        newDateFormat = outputPattern.format(date);
                    }
                    if (currentChild.getNodeName().equalsIgnoreCase("link")) {
                        currentMap.put("link", currentChild.getTextContent());
                        link = (String) currentChild.getTextContent();
                    }
                }
                if (!currentMap.isEmpty()) {
                    items.add(currentMap);
                    newsModel = new NewsModel(link, title, newDateFormat, content, "-1"); //category for a future use
                    dataBaseHelper.addOne(newsModel); //adds each new item in the database
                }
            }
            return items;
        }
    }

    private void showProgressDialog() {
        isLoading = true;
        progressDialog = new ProgressDialog(getContext());
        progressDialog.setCancelable(false);
        progressDialog.setMessage("Ανανέωση");
        progressDialog.show();
    }

    private void hideProgressDialog() {
        isLoading = false;
        if (progressDialog != null) {
            progressDialog.cancel();
        }
    }

    public void getDataFromWeb() {
        GetRssClass fetchRss = new GetRssClass(mFeedUrl);
        fetchRss.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void showToast(String msg) {
        Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
    }}

