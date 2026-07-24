package com.cloudstream.player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.Serializable;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private TextView miniTitle;
    private TextView miniArtist;
    private ImageView mPlay;
    private EditText searchBox;
    private ListView resultList;
    private CircularProgressView progressView;
    public static ArrayList<TrackItem> tracks = new ArrayList<TrackItem>();
    private TrackAdapter adapter;
    private LruCache<String, Bitmap> imageCache;
    private Handler mainHandler = new Handler();
    private boolean isDark;
    private android.content.SharedPreferences themePrefs;


    public static void saveToList(Context context, String listName, TrackItem track) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("CloudStream", Context.MODE_PRIVATE);
        String json = prefs.getString(listName, "[]");
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            // check if already exists
            for(int i=0; i<arr.length(); i++) {
                if(arr.getJSONObject(i).getString("id").equals(track.id)) return;
            }
            org.json.JSONObject obj = new org.json.JSONObject();
            obj.put("id", track.id);
            obj.put("title", track.title);
            obj.put("artist", track.artist);
            obj.put("artworkUrl", track.artworkUrl);
            obj.put("transcodingsUrl", track.transcodingsUrl);
            obj.put("duration", track.duration);
            arr.put(obj);
            prefs.edit().putString(listName, arr.toString()).apply();
        } catch(Exception e) {}
    }

    public static void removeFromList(Context context, String listName, TrackItem track) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("CloudStream", Context.MODE_PRIVATE);
        String json = prefs.getString(listName, "[]");
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            org.json.JSONArray newArr = new org.json.JSONArray();
            for(int i=0; i<arr.length(); i++) {
                if(!arr.getJSONObject(i).getString("id").equals(track.id)) {
                    newArr.put(arr.getJSONObject(i));
                }
            }
            prefs.edit().putString(listName, newArr.toString()).apply();
        } catch(Exception e) {}
    }

    public static boolean isInList(Context context, String listName, TrackItem track) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("CloudStream", Context.MODE_PRIVATE);
        String json = prefs.getString(listName, "[]");
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            for(int i=0; i<arr.length(); i++) {
                if(arr.getJSONObject(i).getString("id").equals(track.id)) return true;
            }
        } catch(Exception e) {}
        return false;
    }

    public static ArrayList<TrackItem> getList(Context context, String listName) {
        ArrayList<TrackItem> list = new ArrayList<>();
        android.content.SharedPreferences prefs = context.getSharedPreferences("CloudStream", Context.MODE_PRIVATE);
        String json = prefs.getString(listName, "[]");
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            for(int i=0; i<arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                TrackItem t = new TrackItem();
                t.id = obj.optString("id", "");
                t.title = obj.optString("title", "");
                t.artist = obj.optString("artist", "");
                String art = obj.optString("artworkUrl", null);
                if ("null".equals(art)) art = null;
                t.artworkUrl = art;

                String trans = obj.optString("transcodingsUrl", null);
                if ("null".equals(trans)) trans = null;
                t.transcodingsUrl = trans;
                t.duration = obj.optInt("duration", 0);
                list.add(t);
            }
        } catch(Exception e) {}
        return list;
    }

    public static class TrackItem implements Serializable {
        public String id;
        public String title;
        public String artist;
        public String artworkUrl;
        public String transcodingsUrl;
        public int duration;
    }

    public static void saveCurrentTracks(android.content.Context ctx) {
        if (tracks == null) return;
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (TrackItem t : tracks) {
                org.json.JSONObject obj = new org.json.JSONObject();
                obj.put("id", t.id);
                obj.put("title", t.title);
                obj.put("artist", t.artist);
                obj.put("artworkUrl", t.artworkUrl);
                obj.put("transcodingsUrl", t.transcodingsUrl);
                obj.put("duration", t.duration);
                arr.put(obj);
            }
            android.content.SharedPreferences prefs = ctx.getSharedPreferences("CloudStream", android.content.Context.MODE_PRIVATE);
            prefs.edit().putString("saved_tracks", arr.toString()).apply();
        } catch(Exception e) {}
    }

    public static void loadCurrentTracks(android.content.Context ctx) {
        try {
            android.content.SharedPreferences prefs = ctx.getSharedPreferences("CloudStream", android.content.Context.MODE_PRIVATE);
            String json = prefs.getString("saved_tracks", null);
            if (json != null) {
                org.json.JSONArray arr = new org.json.JSONArray(json);
                tracks.clear();
                for (int i=0; i<arr.length(); i++) {
                    org.json.JSONObject obj = arr.getJSONObject(i);
                    TrackItem t = new TrackItem();
                    t.id = obj.getString("id");
                    t.title = obj.getString("title");
                    t.artist = obj.getString("artist");
                    t.artworkUrl = obj.getString("artworkUrl");
                    t.transcodingsUrl = obj.getString("transcodingsUrl");
                    t.duration = obj.getInt("duration");
                    tracks.add(t);
                }
            }
        } catch(Exception e) {}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        themePrefs = getSharedPreferences("Settings", Context.MODE_PRIVATE);
        isDark = themePrefs.getBoolean("Dark Theme", true);
        loadCurrentTracks(this);


        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = Math.min(maxMemory / 8, 4 * 1024); // max 4MB cache
        imageCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor((isDark ? Color.parseColor("#0A0A0A") : Color.parseColor("#F5F5F5")));

        // Header Row
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(16, 12, 16, 12);

        ImageView hamburger = new ImageView(this);
        hamburger.setImageResource(android.R.drawable.ic_menu_sort_by_size);
        hamburger.setColorFilter((isDark ? Color.WHITE : Color.parseColor("#121212")));
        hamburger.setPadding(12, 4, 12, 4);
        hamburger.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showSettingsDialog();
            }
        });
        headerRow.addView(hamburger);

        TextView appTitle = new TextView(this);
        android.text.SpannableString sp = new android.text.SpannableString("BerryMusic");
        sp.setSpan(new android.text.style.ForegroundColorSpan(Color.parseColor("#9D4EDD")), 5, 10, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        appTitle.setText(sp);
        appTitle.setTextColor((isDark ? Color.WHITE : Color.parseColor("#121212")));
        appTitle.setTextSize(16);
        appTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        appTitle.setGravity(Gravity.CENTER);
        headerRow.addView(appTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        ImageView searchIconTop = new ImageView(this);
        searchIconTop.setImageResource(android.R.drawable.ic_menu_search);
        searchIconTop.setColorFilter((isDark ? Color.WHITE : Color.parseColor("#121212")));
        searchIconTop.setPadding(12, 4, 12, 4);
        searchIconTop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (searchBox != null) {
                    if (searchBox.getText().toString().trim().length() > 0) {
                        performSearch();
                    } else {
                        searchBox.requestFocus();
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.showSoftInput(searchBox, InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            }
        });
        headerRow.addView(searchIconTop);
        root.addView(headerRow);

        // Content Frame for list and progress
        android.widget.FrameLayout contentFrame = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        root.addView(contentFrame, contentParams);

        resultList = new ListView(this);
        resultList.setDivider(null);
        resultList.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));

        // List Header
        LinearLayout homeHeader = new LinearLayout(this);
        homeHeader.setOrientation(LinearLayout.VERTICAL);



        // Search Box
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setPadding(16, 4, 16, 12);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);

        searchBox = new EditText(this);
        searchBox.setHint("Search songs, artists, albums...");
        searchBox.setHintTextColor((isDark ? Color.parseColor("#B3B3B3") : Color.parseColor("#555555")));
        searchBox.setTextColor((isDark ? Color.WHITE : Color.parseColor("#121212")));
        searchBox.setSingleLine(true);
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBox.setInputType(InputType.TYPE_CLASS_TEXT);
        searchBox.setFocusable(true);
        searchBox.setFocusableInTouchMode(true);
        searchBox.setCursorVisible(true);
        searchBox.setTextSize(11);
        searchBox.setPadding(24, 12, 24, 12);
        android.graphics.drawable.GradientDrawable searchBg = new android.graphics.drawable.GradientDrawable();
        searchBg.setColor((isDark ? Color.parseColor("#1E1E1E") : Color.WHITE));
        searchBg.setCornerRadius(48f);
        searchBox.setBackgroundDrawable(searchBg);
        searchRow.addView(searchBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        // Hidden search button for logic
        Button searchBtn = new Button(this);
        searchBtn.setText("GO");
        searchRow.addView(searchBtn, new LinearLayout.LayoutParams(0, 0));
        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                performSearch();
            }
        });
        searchBox.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                    performSearch();
                    return true;
                }
                return false;
            }
        });
        homeHeader.addView(searchRow);

        // Sections
        String[] sections = {"Recently Played", "Trending Now", "Top Global Artists", "Mood Playlists", "Workout & Gym", "Chill & Relax"};
        String[][] sectionTitles = {
                {"Arijit Singh Hits", "Night Drive", "The Highlights", "Lofi Beats", "Bollywood Top 50", "Workout Motivation", "Classical Masterpieces", "90s Golden Hits"},
                {"Punjabi Top 50", "Pop Hits", "Chill Vibes", "Viral 50", "Indie Favourites", "Sleep Music", "Jazz Classics", "Rap Caviar"},
                {"Taylor Swift", "The Weeknd", "Drake", "Bad Bunny", "Ed Sheeran", "Ariana Grande", "Post Malone", "Dua Lipa"},
                {"Happy Hits", "Focus Beats", "Late Night Vibes", "Morning Coffee", "Gym Pump", "Relax & Unwind", "Romantic Melodies", "Study Lofi"},
                {"Beast Mode", "Running Hits", "Cardio Blast", "Heavy Lifting", "Yoga Flow", "Pre-Workout", "Adrenaline", "Fitness Pop"},
                {"Acoustic Covers", "Rainy Day", "Coffeehouse", "Deep Focus", "Piano Mellow", "Sleep Ambient", "Lo-Fi Beats", "Soft Pop"}
        };
        String[][] sectionSubs = {
                {"25 Songs", "20 Songs", "18 Songs", "30 Songs", "50 Songs", "40 Songs", "15 Songs", "35 Songs"},
                {"50 Songs", "30 Songs", "24 Songs", "50 Songs", "22 Songs", "10 Songs", "45 Songs", "60 Songs"},
                {"Artist", "Artist", "Artist", "Artist", "Artist", "Artist", "Artist", "Artist"},
                {"Playlist", "Playlist", "Playlist", "Playlist", "Playlist", "Playlist", "Playlist", "Playlist"},
                {"Playlist", "Playlist", "Playlist", "Playlist", "Playlist", "Playlist", "Playlist", "Playlist"},
                {"Playlist", "Playlist", "Playlist", "Playlist", "Playlist", "Playlist", "Playlist", "Playlist"}
        };
        for(int s=0; s<6; s++) {
            LinearLayout secHeader = new LinearLayout(this);
            secHeader.setOrientation(LinearLayout.HORIZONTAL);
            secHeader.setPadding(16, 4, 16, 4);
            TextView sTitle = new TextView(this);
            sTitle.setText(sections[s]);
            sTitle.setTextColor((isDark ? Color.WHITE : Color.parseColor("#121212")));
            sTitle.setTextSize(12);
            sTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            secHeader.addView(sTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            TextView sViewAll = new TextView(this);
            sViewAll.setText("View all");
            final String sectionName = sections[s];
            sViewAll.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (searchBox != null) {
                        searchBox.setText(sectionName);
                        performSearch();
                    }
                }
            });
            sViewAll.setTextColor(Color.parseColor("#9D4EDD"));
            sViewAll.setTextSize(9);
            secHeader.addView(sViewAll);
            homeHeader.addView(secHeader);

            android.widget.HorizontalScrollView hScroll = new android.widget.HorizontalScrollView(this);
            hScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout hRow = new LinearLayout(this);
            hRow.setOrientation(LinearLayout.HORIZONTAL);
            hRow.setPadding(16, 0, 16, 12);

            for(int i=0; i<8; i++) {
                LinearLayout albumCard = new LinearLayout(this);
                albumCard.setOrientation(LinearLayout.VERTICAL);
                albumCard.setPadding(0, 0, 12, 0);

                final String q = sectionTitles[s][i];
                albumCard.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        searchBox.setText(q);
                        performSearch();
                    }
                });

                android.widget.FrameLayout imageContainer = new android.widget.FrameLayout(this);
                int size = (int)(getResources().getDisplayMetrics().density * 45);

                final ImageView cover = new ImageView(this);
                cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setColor((isDark ? Color.parseColor("#333333") : Color.parseColor("#E8E8E8")));
                bg.setCornerRadius(16f);
                cover.setBackgroundDrawable(bg);
                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    cover.setClipToOutline(true);
                }

                String seed = q.replace(" ", "");
                String dummyUrl = "https://picsum.photos/seed/" + seed + "/200";

                SoundCloudAPI.getTrackArtwork(dummyUrl, new android.os.Handler(android.os.Looper.getMainLooper()), new SoundCloudAPI.ArtworkCallback() {
                    @Override
                    public void onSuccess(android.graphics.Bitmap bitmap) {
                        if (bitmap != null) {
                            cover.setImageBitmap(bitmap);
                        }
                    }
                    @Override
                    public void onError(String error) {}
                });
                imageContainer.addView(cover, new android.widget.FrameLayout.LayoutParams(size, size));

                ImageView playIcon = new ImageView(this);
                playIcon.setImageResource(android.R.drawable.ic_media_play);
                android.graphics.drawable.GradientDrawable playBg = new android.graphics.drawable.GradientDrawable();
                playBg.setColor(Color.parseColor("#66000000")); // Dark overlay for visibility
                playBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                playIcon.setBackgroundDrawable(playBg);
                android.widget.FrameLayout.LayoutParams playParams = new android.widget.FrameLayout.LayoutParams(40, 40);
                playParams.gravity = Gravity.BOTTOM | Gravity.RIGHT;
                playParams.setMargins(0, 0, 0, 0); playParams.gravity = Gravity.CENTER;
                imageContainer.addView(playIcon, playParams);

                albumCard.addView(imageContainer, new LinearLayout.LayoutParams(size, size));

                TextView aTitle = new TextView(this);
                aTitle.setText(q);
                aTitle.setTextColor((isDark ? Color.WHITE : Color.parseColor("#121212")));
                aTitle.setTextSize(10);
                aTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                aTitle.setPadding(0, 4, 0, 0);
                aTitle.setSingleLine(true);
                aTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
                albumCard.addView(aTitle);

                TextView aArt = new TextView(this);
                aArt.setText(sectionSubs[s][i]);
                aArt.setTextColor((isDark ? Color.parseColor("#B3B3B3") : Color.parseColor("#555555")));
                aArt.setTextSize(8);
                albumCard.addView(aArt);

                hRow.addView(albumCard);
            }
            hScroll.addView(hRow);
            homeHeader.addView(hScroll);
        }

        resultList.addHeaderView(homeHeader, null, false);
        adapter = new TrackAdapter();
        resultList.setAdapter(adapter);
        contentFrame.addView(resultList, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progressView = new CircularProgressView(this);
        android.widget.FrameLayout.LayoutParams progParams = new android.widget.FrameLayout.LayoutParams(100, 100);
        progParams.gravity = Gravity.CENTER;
        progressView.setLayoutParams(progParams);
        progressView.setVisibility(View.GONE);
        contentFrame.addView(progressView);

        // Mini Player
        LinearLayout miniPlayer = new LinearLayout(this);
        miniPlayer.setOrientation(LinearLayout.HORIZONTAL);
        miniPlayer.setBackgroundColor((isDark ? Color.parseColor("#1E1E1E") : Color.WHITE));
        miniPlayer.setPadding(16, 8, 16, 8);
        miniPlayer.setGravity(Gravity.CENTER_VERTICAL);
        android.graphics.drawable.GradientDrawable miniBg = new android.graphics.drawable.GradientDrawable();
        miniBg.setColor((isDark ? Color.parseColor("#1E1E1E") : Color.WHITE));
        miniBg.setCornerRadius(32f);
        miniPlayer.setBackgroundDrawable(miniBg);
        LinearLayout.LayoutParams miniParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        miniParams.setMargins(16, 0, 16, 8);

        ImageView miniArt = new ImageView(this);
        miniArt.setImageResource(android.R.drawable.ic_media_play);
        miniArt.setPadding(4,4,4,4);
        android.graphics.drawable.GradientDrawable miniArtBg = new android.graphics.drawable.GradientDrawable();
        miniArtBg.setColor((isDark ? Color.parseColor("#333333") : Color.parseColor("#E8E8E8")));
        miniArtBg.setCornerRadius(8f);
        miniArt.setBackgroundDrawable(miniArtBg);
        miniPlayer.addView(miniArt, new LinearLayout.LayoutParams(40, 40));

        LinearLayout miniText = new LinearLayout(this);
        miniText.setOrientation(LinearLayout.VERTICAL);
        miniText.setPadding(8, 0, 8, 0);
        miniTitle = new TextView(this);
        miniTitle.setText("Perfect");
        miniTitle.setTextColor((isDark ? Color.WHITE : Color.parseColor("#121212")));
        miniTitle.setTextSize(11);
        miniTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        miniText.addView(miniTitle);
        miniArtist = new TextView(this);
        miniArtist.setText("Ed Sheeran");
        miniArtist.setTextColor((isDark ? Color.parseColor("#B3B3B3") : Color.parseColor("#555555")));
        miniArtist.setTextSize(9);
        miniText.addView(miniArtist);
        miniPlayer.addView(miniText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        mPlay = new ImageView(this);
        mPlay.setImageResource(android.R.drawable.ic_media_play);
        mPlay.setColorFilter((isDark ? Color.WHITE : Color.parseColor("#121212")));
        mPlay.setPadding(8, 0, 8, 0);
        miniPlayer.addView(mPlay);
        mPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (PlayerActivity.player != null) {
                    if (PlayerActivity.player.isPlaying()) {
                        PlayerActivity.player.pause();
                        mPlay.setImageResource(android.R.drawable.ic_media_play);
                    } else {
                        PlayerActivity.player.start();
                        mPlay.setImageResource(android.R.drawable.ic_media_pause);
                    }
                }
            }
        });


        miniPlayer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (PlayerActivity.track != null) {
                    Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                    intent.putExtra("track", PlayerActivity.track);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                } else {
                    Toast.makeText(MainActivity.this, "No track playing", Toast.LENGTH_SHORT).show();
                }
            }
        });
        root.addView(miniPlayer, miniParams);


        // Bottom Nav
        LinearLayout bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setBackgroundColor((isDark ? Color.parseColor("#0A0A0A") : Color.parseColor("#F5F5F5")));
        bottomNav.setPadding(0, 8, 0, 8);

        int[] navIcons = {android.R.drawable.ic_menu_view, android.R.drawable.ic_menu_search, android.R.drawable.btn_star, android.R.drawable.ic_menu_gallery};
        String[] navLabels = {"Home", "Search", "Favorites", "Library"};
        int[] navColors = {Color.parseColor("#9D4EDD"), (isDark ? Color.parseColor("#B3B3B3") : Color.parseColor("#555555")), (isDark ? Color.parseColor("#B3B3B3") : Color.parseColor("#555555")), (isDark ? Color.parseColor("#B3B3B3") : Color.parseColor("#555555"))};

        final ImageView[] navImageViews = new ImageView[4];
        final TextView[] navTextViews = new TextView[4];

        for (int i=0; i<4; i++) {
            LinearLayout navItem = new LinearLayout(this);
            navItem.setOrientation(LinearLayout.VERTICAL);
            navItem.setGravity(Gravity.CENTER);

            final int idx = i;
            navItem.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    for(int j=0; j<4; j++) {
                        if (j == idx) {
                            navImageViews[j].setColorFilter(Color.parseColor("#9D4EDD"));
                            navTextViews[j].setTextColor(Color.parseColor("#9D4EDD"));
                        } else {
                            navImageViews[j].setColorFilter((isDark ? Color.parseColor("#B3B3B3") : Color.parseColor("#555555")));
                            navTextViews[j].setTextColor((isDark ? Color.parseColor("#B3B3B3") : Color.parseColor("#555555")));
                        }
                    }

                    if (idx == 0) {
                        // Home
                        resultList.setSelection(0);
                    } else if (idx == 1) {
                        // Search
                        if (searchBox != null) {
                            searchBox.requestFocus();
                            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                            imm.showSoftInput(searchBox, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                            resultList.setSelection(0);
                        }
                    } else if (idx == 2) {
                        // Favorites
                        ArrayList<TrackItem> favs = getList(MainActivity.this, "favorites");
                        if (favs.isEmpty()) {
                            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(MainActivity.this);
                            builder.setTitle("Favorites");
                            builder.setMessage("Your favorites list is currently empty. Like some songs to see them here.");
                            builder.setPositiveButton("OK", null);
                            builder.show();
                        } else {
                            tracks.clear();
                            tracks.addAll(favs);
                            saveCurrentTracks(MainActivity.this);
                            adapter.notifyDataSetChanged();
                        }
                    } else if (idx == 3) {
                        // Library
                        ArrayList<TrackItem> lib = getList(MainActivity.this, "library");
                        if (lib.isEmpty()) {
                            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(MainActivity.this);
                            builder.setTitle("Library");
                            builder.setMessage("Your offline library is empty.");
                            builder.setPositiveButton("OK", null);
                            builder.show();
                        } else {
                            tracks.clear();
                            tracks.addAll(lib);
                            saveCurrentTracks(MainActivity.this);
                            adapter.notifyDataSetChanged();
                        }
                    }

                }
            });

            ImageView icon = new ImageView(this);
            icon.setImageResource(navIcons[i]);
            icon.setColorFilter(navColors[i]);
            icon.setPadding(0, 0, 0, 0);
            int iconSize = (int)(getResources().getDisplayMetrics().density * 22);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconParams.setMargins(0, 4, 0, 4);
            navItem.addView(icon, iconParams);
            navImageViews[i] = icon;

            TextView label = new TextView(this);
            label.setText(navLabels[i]);
            label.setTextColor(navColors[i]);
            navTextViews[i] = label;
            label.setTextSize(8);
            label.setGravity(Gravity.CENTER);
            navItem.addView(label);

            bottomNav.addView(navItem, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        }
        root.addView(bottomNav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);





        resultList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                TrackItem item = tracks.get(position);
                if (item.transcodingsUrl == null || item.transcodingsUrl.equals("null") || item.transcodingsUrl.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Stream unavailable", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                intent.putExtra("track", item);
                startActivity(intent);
            }
        });
    }


    private void showSettingsDialog() {
        final android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        android.widget.FrameLayout dialogRoot = new android.widget.FrameLayout(this);
        dialogRoot.setBackgroundColor((isDark ? Color.parseColor("#88000000") : Color.parseColor("#33000000")));

        final android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        int width = (int)(getResources().getDisplayMetrics().widthPixels * 0.75);
        android.widget.FrameLayout.LayoutParams panelParams = new android.widget.FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT);
        panelParams.gravity = Gravity.LEFT;
        scrollView.setLayoutParams(panelParams);
        scrollView.setBackgroundColor((isDark ? Color.parseColor("#1A1A1A") : Color.parseColor("#E0E0E0")));

        final LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(panel);

        TextView title = new TextView(this);
        title.setText("Settings");
        title.setTextColor((isDark ? Color.WHITE : Color.parseColor("#121212")));
        title.setTextSize(20);
        title.setPadding(32, 48, 32, 48);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        panel.addView(title);

        String[] sets = {"High Quality Audio", "Dark Theme", "Offline Mode", "Data Saver"};
        for(String s : sets) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(32, 32, 32, 32);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView t = new TextView(this);
            t.setText(s);
            t.setTextColor((isDark ? Color.WHITE : Color.parseColor("#121212")));
            t.setTextSize(16);
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));


            android.widget.Switch sw = new android.widget.Switch(this);
            sw.setChecked(themePrefs.getBoolean(s, s.equals("Dark Theme")));
            final String prefKey = s;
            sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                    themePrefs.edit().putBoolean(prefKey, isChecked).apply();
                    if(prefKey.equals("Dark Theme")) { recreate(); }
                }
            });
            row.addView(sw);


            panel.addView(row);
        }

        TextView aboutTitle = new TextView(this);
        aboutTitle.setText("About");
        aboutTitle.setTextColor(Color.parseColor("#9D4EDD"));
        aboutTitle.setTextSize(14);
        aboutTitle.setPadding(32, 48, 32, 16);
        panel.addView(aboutTitle);

        TextView aboutDesc = new TextView(this);
        aboutDesc.setText("Developer: John\nLicense: MIT License\nVersion: 1.0.0");
        aboutDesc.setTextColor((isDark ? Color.parseColor("#BBBBBB") : Color.parseColor("#666666")));
        aboutDesc.setTextSize(14);
        aboutDesc.setPadding(32, 0, 32, 32);
        panel.addView(aboutDesc);

        dialogRoot.addView(scrollView);

        dialogRoot.setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View v) { dialog.dismiss(); }
        });
        scrollView.setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View v) {}
        });

        dialog.setContentView(dialogRoot);
        dialog.show();

        android.view.animation.TranslateAnimation anim = new android.view.animation.TranslateAnimation(
                android.view.animation.Animation.RELATIVE_TO_PARENT, -1.0f,
                android.view.animation.Animation.RELATIVE_TO_PARENT, 0.0f,
                android.view.animation.Animation.RELATIVE_TO_PARENT, 0.0f,
                android.view.animation.Animation.RELATIVE_TO_PARENT, 0.0f
        );
        anim.setDuration(300);
        scrollView.startAnimation(anim);
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (PlayerActivity.track == null) {
            android.content.SharedPreferences prefs = getSharedPreferences("CloudStream", MODE_PRIVATE);
            if (prefs.contains("track_id")) {
                TrackItem t = new TrackItem();
                t.id = prefs.getString("track_id", "");
                t.title = prefs.getString("track_title", "");
                t.artist = prefs.getString("track_artist", "");
                t.artworkUrl = prefs.getString("track_artworkUrl", "");
                t.transcodingsUrl = prefs.getString("track_transcodingsUrl", "");
                t.duration = prefs.getInt("track_duration", 0);
                PlayerActivity.track = t;
            }
        }

        if (PlayerActivity.track != null && miniTitle != null) {
            miniTitle.setText(PlayerActivity.track.title);
            miniArtist.setText(PlayerActivity.track.artist);
            if (PlayerActivity.player != null && PlayerActivity.player.isPlaying()) {
                mPlay.setImageResource(android.R.drawable.ic_media_pause);
            } else {
                mPlay.setImageResource(android.R.drawable.ic_media_play);
            }
        }
    }

    private void performSearch() {
        String q = searchBox.getText().toString().trim();
        if (q.isEmpty()) return;

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchBox.getWindowToken(), 0);

        tracks.clear();
        adapter.notifyDataSetChanged();
        progressView.setVisibility(View.VISIBLE);
        progressView.startAnimation();

        SoundCloudAPI.searchTracks(q, mainHandler, new SoundCloudAPI.SearchCallback() {
            @Override
            public void onSuccess(ArrayList<TrackItem> results) {
                progressView.stopAnimation();
                progressView.setVisibility(View.GONE);
                tracks.clear();
                tracks.addAll(results);
                saveCurrentTracks(MainActivity.this);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onError(String error) {
                progressView.stopAnimation();
                progressView.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private class TrackAdapter extends BaseAdapter {
        @Override
        public int getCount() { return tracks.size(); }
        @Override
        public Object getItem(int position) { return tracks.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout layout;
            TextView indexView;
            final ImageView imageView;
            TextView titleView;
            TextView artistView;

            if (convertView == null) {
                layout = new LinearLayout(MainActivity.this);
                layout.setOrientation(LinearLayout.HORIZONTAL);
                layout.setPadding(16, 8, 16, 8);
                layout.setGravity(Gravity.CENTER_VERTICAL);

                indexView = new TextView(MainActivity.this);
                indexView.setId(2001);
                indexView.setTextColor((isDark ? Color.parseColor("#B3B3B3") : Color.parseColor("#555555")));
                indexView.setTextSize(12);
                indexView.setPadding(0, 0, 8, 0);
                layout.addView(indexView);

                imageView = new ImageView(MainActivity.this);
                imageView.setId(1001);
                int size = (int)(getResources().getDisplayMetrics().density * 40);
                layout.addView(imageView, new LinearLayout.LayoutParams(size, size));

                LinearLayout textLayout = new LinearLayout(MainActivity.this);
                textLayout.setOrientation(LinearLayout.VERTICAL);
                textLayout.setPadding(12, 0, 0, 0);

                titleView = new TextView(MainActivity.this);
                titleView.setId(1002);
                titleView.setTextColor((isDark ? Color.WHITE : Color.parseColor("#121212")));
                titleView.setTextSize(13);
                titleView.setSingleLine(true);
                titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
                textLayout.addView(titleView);

                artistView = new TextView(MainActivity.this);
                artistView.setId(1003);
                artistView.setTextColor((isDark ? Color.parseColor("#B3B3B3") : Color.parseColor("#555555")));
                artistView.setTextSize(11);
                artistView.setSingleLine(true);
                artistView.setEllipsize(android.text.TextUtils.TruncateAt.END);
                textLayout.addView(artistView);

                layout.addView(textLayout, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

                TextView playBtn = new TextView(MainActivity.this);
                playBtn.setText(">");
                playBtn.setTextColor(Color.parseColor("#00E5FF")); // Cyan
                playBtn.setTextSize(16);
                playBtn.setPadding(8, 0, 0, 0);
                layout.addView(playBtn);

                convertView = layout;
            } else {
                layout = (LinearLayout) convertView;
                indexView = (TextView) layout.findViewById(2001);
                imageView = (ImageView) layout.findViewById(1001);
                titleView = (TextView) layout.findViewById(1002);
                artistView = (TextView) layout.findViewById(1003);
            }

            final TrackItem item = tracks.get(position);
            indexView.setText(String.valueOf(position + 1));
            titleView.setText(item.title);
            artistView.setText(item.artist);

            imageView.setImageBitmap(null);
            imageView.setBackgroundColor(Color.DKGRAY);

            if (item.artworkUrl != null && !item.artworkUrl.isEmpty()) {
                Bitmap cached = imageCache.get(item.artworkUrl);
                if (cached != null) {
                    imageView.setImageBitmap(getRoundedBitmap(cached));
                    imageView.setBackgroundColor(Color.TRANSPARENT);
                } else {
                    imageView.setTag(item.artworkUrl);
                    SoundCloudAPI.getTrackArtwork(item.artworkUrl, mainHandler, new SoundCloudAPI.ArtworkCallback() {
                        @Override
                        public void onSuccess(Bitmap bitmap) {
                            if (bitmap != null) {
                                imageCache.put(item.artworkUrl, bitmap);
                                if (item.artworkUrl.equals(imageView.getTag())) {
                                    imageView.setImageBitmap(getRoundedBitmap(bitmap));
                                    imageView.setBackgroundColor(Color.TRANSPARENT);
                                }
                            }
                        }
                        @Override
                        public void onError(String error) {}
                    });
                }
            }

            return convertView;
        }

        private Bitmap getRoundedBitmap(Bitmap bitmap) {
            Bitmap rounded = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(rounded);
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setAntiAlias(true);
            paint.setShader(new android.graphics.BitmapShader(bitmap, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRoundRect(new android.graphics.RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()), 12, 12, paint);
            return rounded;
        }
    }
}
