package com.example.marietje;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.example.marietje.MediaContract.MediaEntry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class SongActivity extends ActionBarActivity {
    private static final String MEDIA_URL = "http://marietje.marie-curie.nl:8080/media";

    private ViewPager mPager;
    private PagerAdapter mPagerAdapter;

    public class MediaReader extends AsyncTask<String, Void, String> {
        private ProgressDialog dialog;

        @Override
        protected void onPreExecute() {
            dialog = new ProgressDialog(SongActivity.this);
            dialog.setMessage("Lijst met liedjes downloaden..");
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.show();
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            dialog.setMessage("Database updaten...");
            super.onProgressUpdate(values);
        }

        @Override
        protected String doInBackground(String... urls) {
            InputStream inputStream;
            try {
                inputStream = Utils.downloadUrl(urls[0]);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, "UTF-8"), 8);
                StringBuilder sb = new StringBuilder();

                String line = null;
                while ((line = reader.readLine()) != null) {
                    sb.append(line + "\n");
                }

                String result = sb.toString();
                inputStream.close();

                JSONObject jObject = new JSONObject(result);

                MediaDbHelper mediaDbHelper = new MediaDbHelper(
                        SongActivity.this);
                SQLiteDatabase db = mediaDbHelper.getWritableDatabase();

                Log.i(MediaReader.class.getName(), "Importing songs");

                publishProgress();

                JSONArray names = jObject.names();

                try {
                    db.beginTransaction(); // speeds up insertion
                    db.delete(MediaEntry.TABLE_NAME, null, null); // clear table

                    for (int i = 0; i < names.length(); i++) {
                        JSONArray vals = jObject.getJSONArray(names
                                .getString(i));

                        String title = vals.getString(1).trim();
                        String artist = vals.getString(0).trim();

                        if (title == "" || artist == "") {
                            continue; // skip faulty track
                        }

                        ContentValues values = new ContentValues();
                        values.put(MediaEntry.COLUMN_NAME_ENTRY_ID, names
                                .getString(i).substring(1)); // remove the "_"
                        values.put(MediaEntry.COLUMN_NAME_TITLE,
                                title);
                        values.put(MediaEntry.COLUMN_NAME_ARTIST,
                                artist);

                        db.insert(MediaEntry.TABLE_NAME, null, values);
                    }

                    db.setTransactionSuccessful();
                } catch (SQLException e) {
                } finally {
                    db.endTransaction();
                }

                db.close();
                Log.i(MediaReader.class.getName(), "Importing done");

            } catch (IOException e) {
                Toast.makeText(SongActivity.this,
                        "Kon de lijst met liedjes niet downloaden.",
                        Toast.LENGTH_SHORT).show();
            } catch (JSONException e) {
                Log.e(SongActivity.class.getName(), "Fout bij JSON parsing", e);
                Toast.makeText(SongActivity.this,
                        "Kon de lijst met liedjes niet downloaden.",
                        Toast.LENGTH_SHORT).show();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            dialog.dismiss();
            // TODO: load songs in fragment
        }
    }

    class MyPageAdapter extends FragmentPagerAdapter {
        private List<Fragment> fragments;

        public MyPageAdapter(FragmentManager fm, List<Fragment> fragments) {
            super(fm);
            this.fragments = fragments;
        }

        @Override
        public Fragment getItem(int position) {
            return this.fragments.get(position);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (position == 0)
                return "Aanvragen";
            if (position == 1)
                return "Queue";
            return "";
        }

        @Override
        public int getCount() {
            return this.fragments.size();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_song);

        // FIXME: start in login screen
        SharedPreferences settings = getSharedPreferences("prefs", 0);
        if (!settings.contains("username")) {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
        }

        List<Fragment> fragments = new ArrayList<Fragment>();
        fragments.add(Fragment.instantiate(this,
                SongListFragment.class.getName()));
        fragments.add(Fragment.instantiate(this,
                QueueListFragment.class.getName()));
        mPagerAdapter = new MyPageAdapter(super.getSupportFragmentManager(),
                fragments);

        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(mPagerAdapter);
    }

    @Override
    protected void onResume() {
        SharedPreferences settings = getSharedPreferences("prefs", 0);
        if (settings.contains("username")) {

			/* Check if the database if empty, if so, update */
            MediaDbHelper mediaDbHelper = new MediaDbHelper(SongActivity.this);
            if (mediaDbHelper.isEmpty()) {
                Log.i("", "Empty database, download songs");
                new MediaReader().execute(MEDIA_URL);
                // TODO: update list
            }
        }

        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.song, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.refresh:
                new MediaReader().execute(MEDIA_URL);
                return true;
            case R.id.logout:
                SharedPreferences settings = getSharedPreferences("prefs", 0);
                settings.edit().clear().commit();
                Intent intent = new Intent(this, LoginActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}