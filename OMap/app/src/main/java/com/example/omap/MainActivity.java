package com.example.omap;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.mapsforge.core.util.LRUCache;
import org.mapsforge.map.android.rendertheme.AssetsRenderTheme;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.InternalRenderTheme;
import org.mapsforge.map.rendertheme.XmlRenderTheme;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.mapsforge.MapsForgeTileProvider;
import org.osmdroid.mapsforge.MapsForgeTileSource;
import org.osmdroid.tileprovider.constants.OpenStreetMapTileProviderConstants;
import org.osmdroid.tileprovider.modules.MapTileDownloader;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.tileprovider.util.CloudmadeUtil;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.TileSystem;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.TilesOverlay;
import org.osmdroid.views.util.constants.OverlayConstants;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class MainActivity extends WearableActivity {

    private final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    private MapView mMapView = null;
    private static final String TAG = "MainActivity";

    static int OMAP_REQ_CODE = 69;

    MapsForgeTileSource fromFiles = null;
    MapsForgeTileProvider forge = null;


    Connection con;
    final Handler myHandler = new Handler();

    int rotationValue = 0;
    int rotationValuePrev = -1;
    RotationDial rotation;

    LatLon latlon;

    static double latKelowna = 0.017552469694550155;
    static double lonKelowna = 0.027272558593750773;
    static double zoomAtBoot = 14.05020300581456;
    static double maxZoomLevel = 18.0;
    static double minZoomLevel = 4.0;


    int x = 0;
    int y = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //handle permissions first, before map is created.
        this.requestPermissions(new String[]{READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE}, OMAP_REQ_CODE);

//        setting user agent, with app's package name etc
        Configuration.getInstance().load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this));

//        for debugging 2020.04.28
        Configuration.getInstance().setDebugMode(true);
        Configuration.getInstance().setDebugMapView(true);
        Configuration.getInstance().setDebugTileProviders(true);
        Configuration.getInstance().setDebugMapTileDownloader(true);
//        end

        Log.d(TAG, "onCreate: osmdroid basepath absolutepath:" + Configuration.getInstance().getOsmdroidBasePath().getAbsolutePath() + " can read:" + Configuration.getInstance().getOsmdroidBasePath().canRead() + " exists:" + Configuration.getInstance().getOsmdroidBasePath().exists() + " isfile:" + Configuration.getInstance().getOsmdroidBasePath().isFile());
        Log.d(TAG, "onCreate: basepath canwrite:" + Configuration.getInstance().getOsmdroidBasePath().canWrite() + " isdir:" + Configuration.getInstance().getOsmdroidBasePath().isDirectory());
        Log.d(TAG, "onCreate: basepath ls:" + Arrays.toString(Configuration.getInstance().getOsmdroidBasePath().list()));


        Log.d(TAG, "onCreate: tilecache absolutpath:" + Configuration.getInstance().getOsmdroidTileCache().getAbsolutePath() + " canread:" + Configuration.getInstance().getOsmdroidTileCache().canRead() + " canwrite:" + Configuration.getInstance().getOsmdroidTileCache().canWrite());

        Log.d(TAG, "onCreate: tile cache usable space:" + Configuration.getInstance().getOsmdroidTileCache().getUsableSpace());



        //setting this before the layout is inflated is a good idea
        //it 'should' ensure that the map has a writable location for the map cache, even without permissions
        //if no tiles are displayed, you can try overriding the cache path using Configuration.getInstance().setCachePath

        //see also StorageUtils
        //note, the load method also sets the HTTP User Agent to your application's package name, abusing osm's
        //tile servers will get you banned based on this string

        //inflate and create the map
        setContentView(R.layout.activity_main);

        MapsForgeTileSource.createInstance(this.getApplication());

        rotation = new RotationDial();
        latlon = new LatLon(latKelowna, lonKelowna, zoomAtBoot);
        Log.d(TAG, "onCreate: latlon:" + latlon);

        File[] maps = new File[0];
        File m = new File("sdcard/osmdroid/british-columbia.map");
        if(m.exists()) {
            maps = new File[]{m};
        } else {
            Log.d(TAG, "onCreate: file does not exist");
        }

        for (File ff: maps) {
            Log.d(TAG, "onCreate: path:" + ff.getAbsolutePath() + " is file:" + ff.isFile() + " can read:" + ff.canRead() + " exists:" + ff.exists());
        }

        XmlRenderTheme theme = null; //null is ok here, uses the default rendering theme if it's not set
        try {
//this file should be picked up by the mapsforge dependencies
            theme = new AssetsRenderTheme(this, "renderthemes/", "rendertheme-v4.xml");
            //alternative: theme = new ExternalRenderTheme(userDefinedRenderingFile);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        final MapsForgeTileSource fromFiles = MapsForgeTileSource.createFromFiles(maps, theme, "rendertheme-v4");
//        final MapsForgeTileSource fromFiles = MapsForgeTileSource.createFromFiles(maps);

        MapsForgeTileProvider forge = new MapsForgeTileProvider(
                new SimpleRegisterReceiver(this),
                fromFiles, null);


        mMapView = findViewById(R.id.map);
        mMapView.setUseDataConnection(false); // ensuring data never fetched online

        //        clearing cache 2020.05.02
        mMapView.getTileProvider().clearTileCache();
        //       end


        mMapView.setTileProvider(forge);
        mMapView.getTileProvider().ensureCapacity(400);



        mMapView.getTileProvider().createTileCache();
//        loading background 2020.05.04
        mMapView.getOverlayManager().getTilesOverlay().setLoadingBackgroundColor(android.R.color.black);
        mMapView.getOverlayManager().getTilesOverlay().setLoadingLineColor(Color.argb(255, 0, 255, 0));
//      end

//        mMapView.getTileProvider().getTileCache().setAutoEnsureCapacity(true);
        Log.d(TAG, "onCreate: cache size:" + mMapView.getTileProvider().getTileCache().getSize());

        Log.d(TAG, "onCreate: after setup offline");
        Log.d(TAG, "onCreate: osmdroid basepath absolutepath:" + Configuration.getInstance().getOsmdroidBasePath().getAbsolutePath() + " can read:" + Configuration.getInstance().getOsmdroidBasePath().canRead() + " exists:" + Configuration.getInstance().getOsmdroidBasePath().exists() + " isfile:" + Configuration.getInstance().getOsmdroidBasePath().isFile());
        Log.d(TAG, "onCreate: basepath canwrite:" + Configuration.getInstance().getOsmdroidBasePath().canWrite() + " isdir:" + Configuration.getInstance().getOsmdroidBasePath().isDirectory());
        Log.d(TAG, "onCreate: basepath ls:" + Arrays.toString(Configuration.getInstance().getOsmdroidBasePath().list()));


        Log.d(TAG, "onCreate: tilecache absolutpath:" + Configuration.getInstance().getOsmdroidTileCache().getAbsolutePath() + " canread:" + Configuration.getInstance().getOsmdroidTileCache().canRead() + " canwrite:" + Configuration.getInstance().getOsmdroidTileCache().canWrite());

        Log.d(TAG, "onCreate: tile cache usable space:" + Configuration.getInstance().getOsmdroidTileCache().getUsableSpace());

        //now for a magic trick
        //since we have no idea what will be on the
        //user's device and what geographic area it is, this will attempt to center the map
        //on whatever the map data provides
        mMapView.post(
                new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "run: mapview post runnable run called");
                        mMapView.zoomToBoundingBox(fromFiles.getBoundsOsmdroid(), false);
//                        updateMap();
//                        IMapController mapController = mMapView.getController();
//                        mapController.setZoom(latlon.zoom);
//                            BoundingBox b = getBoundingBox(new GeoPoint(latlon.lat - 0.0005, latlon.lon - 0.0005), new GeoPoint(latlon.lat + 0.0005, latlon.lon + 0.0005));
//                        final IGeoPoint center = b.getCenterWithDateLine();

//                        mMapView.zoomToBoundingBox(b, false);

//                        mMapView.getController().setZoom(zoomAtBoot);
                    }


                });



        initSensorConn(200, 2000);

        // Enables Always-on
//        setAmbientEnabled();
    }

    @Override
    public void onResume() {
        super.onResume();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        mMapView.onResume(); //needed for compass, my location overlays, v6.0.0 and up
    }


    @Override
    public void onPause() {
        super.onPause();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().save(this, prefs);
        mMapView.onPause();  //needed for compass, my location overlays, v6.0.0 and up
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,  @NonNull String[] permissions,  @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void initSensorConn(Integer period, Integer delay) {
        con = new Connection(this);
        con.connect();

        Timer timer = new Timer();
        try {
            timer.schedule(new java.util.TimerTask() {

                @Override
                public void run() {
                    UpdateGUI();
                }
            }, delay, period);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // todo experiment scheduledexecutorservice
    }

    private void UpdateGUI() {
        myHandler.post(myRunnable);
    }

    final Runnable myRunnable = new Runnable() {
        public void run() {
            TimerMethod();
        }
    };

    public int differenceMagnitude(int p) {
        if (p > 300) return 2;
        if (p > 100) return 1;
        if (p < -300) return -2;
        if (p < -100) return -1;
        return 0;
    }


    public void TimerMethod() {

//        updateMap();
//        Log.d(TAG, "updateMap: zoom level:" + mMapView.getZoomLevelDouble() + " lat:" + latlon.lat + " lon:" + latlon.lon);

//        Log.d(TAG, "TimerMethod: zoom level:" + mMapView.getZoomLevelDouble() + " mapview.lat:" + mMapView.getLatitudeSpanDouble() + " mapview.lon:" + mMapView.getLongitudeSpanDouble());
//        Log.d(TAG, "TimerMethod: scrollx:" + mMapView.getScrollX() + " mapscrollx:" + mMapView.getMapScrollX() + " scrolly:" + mMapView.getScrollY() + " mapscrolly:" + mMapView.getMapScrollY());
//        Log.d(TAG, "onCreate: cache size:" + mMapView.getTileProvider().getTileCache().getSize());

//        mMapView.scrollTo(x, y);

        int X = con.getX();
        int Y = con.getY();
//
        rotationValue = con.getRotationValue();
        if (rotationValue < 0) {
            rotationValue = 0;
        }

        if (rotationValuePrev == -1) rotationValuePrev = rotationValue;

        if (rotationValuePrev != rotationValue) {
            int diff = rotationValue-rotationValuePrev;
            double zoom = mMapView.getZoomLevelDouble();
            zoom = zoom + ((double)diff)/100;
            mMapView.getController().setZoom(zoom);

            rotationValuePrev = rotationValue;
        }

//        boolean buttonState = con.getButtonState();

        int magY = differenceMagnitude(Y);
        int magX = differenceMagnitude(X);
//
//        Log.d(TAG, "TimerMethod: x: " + x + " y:" + y + " X:" + X + " Y:" + Y + " magX:" + magX + " magY:" + magY + " rotationValue:" + rotationValue + " rotationValuePrev:" + rotationValuePrev + " socket-connected:" + con.isConnected() + " mapscrollx:" + mMapView.getMapScrollX() + " mapscrolly:" + mMapView.getMapScrollY());

        x -= magY*5;
        y -= magX*5;

    }

    void updateMap() {
        Log.d(TAG, "updateMap: zoom level:" + latlon.zoom + " lat:" + latlon.lat + " lon:" + latlon.lon);
        IMapController mapController = mMapView.getController();
        mapController.setZoom(latlon.zoom);
        GeoPoint startPoint = new GeoPoint(latlon.lat, latlon.lon);

        Log.d(TAG, "updateMap: geopoint:" + startPoint);

        mapController.setCenter(startPoint);
        Log.d(TAG, "updateMap: updated zoom:" + mMapView.getZoomLevelDouble() + " lat:" + mMapView.getLatitudeSpanDouble() + " lon:" + mMapView.getLongitudeSpanDouble());
    }


    static class RotationDial {
        enum State {
            CHANGED, SAME;
        }

        int value;
        State state;

        RotationDial() {
            state = State.SAME;
            value = 0;
        }

        void update(int v) {

            if (v == value) {
                state = State.SAME;
            } else {
                state = State.CHANGED;
            }

            value = v;
        }

        boolean changed() {
            return state == State.CHANGED;
        }
    }

    static class LatLon {
        double lat, lon, zoom;
        static double scalingFactor = 1e5;



        LatLon(){ lat = latKelowna; lon = lonKelowna; this.zoom = zoomAtBoot; }
        LatLon(double lat, double lon) { this.lat = lat; this.lon = lon; this.zoom = zoomAtBoot; }
        LatLon(double lat, double lon, double zoom) { this.lat = lat; this.lon = lon; this.zoom = zoom; }

        public String toString() {return "(" + lat + ", " + lon + ", " + zoom + ")"; }

        void setLat(double lat) {this.lat = lat; }
        void setLon(double lon) {this.lon = lon; }

        void updateLat(double rate) { setLat(this.lat + rate/scalingFactor); }
        void updateLon(double rate) { setLon(this.lon + rate/scalingFactor); }

        double getLat() { return this.lat; }
        double getLon() { return this.lon; }
        double getZoom() { return this.zoom; }
    }


    /**
     * This function finds a BoundingBox that fits both the start and end location points.
     *
     * @param start GeoPoint for start location
     * @param end GeoPoint for end location
     * @return BoundingBox that holds both location points
     */
    private BoundingBox getBoundingBox(GeoPoint start, GeoPoint end) {
        double north;
        double south;
        double east;
        double west;
        if(start.getLatitude() > end.getLatitude()) {
            north = start.getLatitude();
            south = end.getLatitude();
        } else {
            north = end.getLatitude();
            south = start.getLatitude();
        }
        if(start.getLongitude() > end.getLongitude()) {
            east = start.getLongitude();
            west = end.getLongitude();
        } else {
            east = end.getLongitude();
            west = start.getLongitude();
        }
        return new BoundingBox(north, east, south, west);
    }

    /**
     * This function allows the MapView to zoom to show the whole path between
     * the start and end points.
     *
     * @param box BoundingBox for start and end points
     */
// see code attribution
    private void zoomToBounds(final BoundingBox box) {
        if (mMapView.getHeight() > 0) {
            mMapView.zoomToBoundingBox(box, false);
            mMapView.zoomToBoundingBox(box, false);
        } else {
            ViewTreeObserver vto = mMapView.getViewTreeObserver();
            vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mMapView.zoomToBoundingBox(box, false);
                    mMapView.zoomToBoundingBox(box, false);
                    ViewTreeObserver vto2 = mMapView.getViewTreeObserver();
                    vto2.removeOnGlobalLayoutListener(this);
                }
            });
        }
    }


}
