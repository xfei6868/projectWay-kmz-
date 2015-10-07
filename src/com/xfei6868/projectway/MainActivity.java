package com.xfei6868.projectway;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BaiduMap.OnMapLongClickListener;
import com.baidu.mapapi.map.BaiduMapOptions;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.PolygonOptions;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.map.Stroke;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.model.LatLngBounds;
import com.baidu.mapapi.model.LatLngBounds.Builder;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.geocode.GeoCodeResult;
import com.baidu.mapapi.search.geocode.GeoCoder;
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult;
import com.ekito.simpleKML.Serializer;
import com.ekito.simpleKML.model.Boundary;
import com.ekito.simpleKML.model.Coordinate;
import com.ekito.simpleKML.model.Coordinates;
import com.ekito.simpleKML.model.Document;
import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;
import com.ekito.simpleKML.model.Geometry;
import com.ekito.simpleKML.model.GroundOverlay;
import com.ekito.simpleKML.model.Kml;
import com.ekito.simpleKML.model.LineString;
import com.ekito.simpleKML.model.LinearRing;
import com.ekito.simpleKML.model.Model;
import com.ekito.simpleKML.model.MultiGeometry;
import com.ekito.simpleKML.model.MultiTrack;
import com.ekito.simpleKML.model.NetworkLink;
import com.ekito.simpleKML.model.PhotoOverlay;
import com.ekito.simpleKML.model.Placemark;
import com.ekito.simpleKML.model.Point;
import com.ekito.simpleKML.model.Polygon;
import com.ekito.simpleKML.model.ScreenOverlay;
import com.ekito.simpleKML.model.Track;

public class MainActivity extends Activity implements OnGetGeoCoderResultListener {
	public static final int FILE_RESULT_CODE = 1;
	
	private MapView mMapView;
	private BaiduMap mBaiduMap;
	private GeoCoder mSearch; // 搜索模块，也可去掉地图模块独立使用
	private Marker mMarker;
	
	private static final String LTAG = MainActivity.class.getSimpleName();

	/**
	 * 构造广播监听类，监听 SDK key 验证以及网络异常广播
	 */
	public class SDKReceiver extends BroadcastReceiver {
		public void onReceive(Context context, Intent intent) {
			String s = intent.getAction();
			Log.d(LTAG, "action: " + s);
			TextView text = (TextView) findViewById(R.id.textView1);
			text.setTextColor(Color.RED);
			if (s.equals(SDKInitializer.SDK_BROADTCAST_ACTION_STRING_PERMISSION_CHECK_ERROR)) {
				text.setText("key 验证出错! 请在 AndroidManifest.xml 文件中检查 key 设置");
			} else if (s.equals(SDKInitializer.SDK_BROADCAST_ACTION_STRING_NETWORK_ERROR)) {
			}
		}
	}

	private SDKReceiver mReceiver;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		Intent intent = getIntent();
		if (intent.hasExtra("x") && intent.hasExtra("y")) {
			// 当用intent参数时，设置中心点为指定点
			Bundle b = intent.getExtras();
			LatLng p = new LatLng(b.getDouble("y"), b.getDouble("x"));
			mMapView = new MapView(this,
					new BaiduMapOptions().mapStatus(new MapStatus.Builder().target(p).build()));
		} else {
			mMapView = new MapView(this, new BaiduMapOptions());
		}
		MapView mapLayout = (MapView)findViewById(R.id.mapLayout);
		mBaiduMap = mapLayout.getMap();
		// 初始化搜索模块，注册事件监听
		mSearch = GeoCoder.newInstance();
		mSearch.setOnGetGeoCodeResultListener(this);
		
		mBaiduMap.setOnMapLongClickListener(new OnMapLongClickListener() {
			
			@Override
			public void onMapLongClick(LatLng latLng) {
				// 反Geo搜索
				mSearch.reverseGeoCode(new ReverseGeoCodeOption().location(latLng));
				
			}
		});
		
		// 注册 SDK 广播监听者
		IntentFilter iFilter = new IntentFilter();
		iFilter.addAction(SDKInitializer.SDK_BROADTCAST_ACTION_STRING_PERMISSION_CHECK_ERROR);
		iFilter.addAction(SDKInitializer.SDK_BROADCAST_ACTION_STRING_NETWORK_ERROR);
		mReceiver = new SDKReceiver();
		registerReceiver(mReceiver, iFilter);
		
		if (Intent.ACTION_VIEW.equals(intent.getAction())) {
			Uri uri = (Uri)intent.getData();
			final String filename = uri.getPath();
			
			try {
				if (filename.endsWith(".kmz")) {
					InputStream kmzStream = new FileInputStream(filename);
					List<InputStream> kmlStreamList = getKmlStreamsFromKmz(kmzStream);
					for (InputStream kmlStream : kmlStreamList) {
						parseKml(kmlStream);
					}
				} else if (filename.endsWith(".kml")) {
					InputStream kmlStream = new FileInputStream(filename);
					parseKml(kmlStream);
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			mBaiduMap.setOnMapLoadedCallback(new BaiduMap.OnMapLoadedCallback() {
				@Override
				public void onMapLoaded() {
					System.out.println("-----------------------------------------------------------------");
				}
			});
			
		}
	}
	
	private List<InputStream> getKmlStreamsFromKmz(InputStream kmzStream) throws IOException {
		ZipInputStream zipIS = new ZipInputStream(kmzStream);
		List<InputStream> kmlStreamList = new ArrayList<InputStream>();
		ZipEntry zipEntry = null;
		while ((zipEntry = zipIS.getNextEntry()) != null) {
			String zipEntryName = zipEntry.getName(); 
			if (zipEntryName.endsWith("kml")) {
				ByteArrayOutputStream os = new ByteArrayOutputStream();
				byte[] b = new byte[512];
				int readedByteSize = 0;
				while ((readedByteSize = zipIS.read(b)) > 0) { 
					os.write(b, 0, readedByteSize); 
				} 
				os.flush(); 
				os.close(); 

				InputStream kmlStream = new ByteArrayInputStream(os.toByteArray());
				kmlStreamList.add(kmlStream);
			} else if (zipEntryName.endsWith("png")) {
				ByteArrayOutputStream os = new ByteArrayOutputStream(); 
				byte[] b = new byte[512]; 
				int readedByteSize = 0; 
				while ((readedByteSize = zipIS.read(b)) > 0) { 
					os.write(b, 0, readedByteSize); 
				} 
				os.flush(); 
				os.close(); 
				
				InputStream bitmapStream = new ByteArrayInputStream(os.toByteArray());
				//Bitmap bitmap = BitmapFactory.decodeStream(bitmapStream); 
			}
		}
		
		return kmlStreamList;
	}
	
	public void parseKml(InputStream inputStream) {
		Serializer kmlSerializer = new Serializer();
		try {
			Kml kml = kmlSerializer.read(inputStream);
			Feature feature = kml.getFeature();
			
			parseFeature(feature);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void parseFeature(Feature feature) {
		feature.getName();
		feature.getId();
		feature.getDescription();
		
		if (feature instanceof Document) {
			Document document = (Document)feature;
			TextView textView = (TextView)findViewById(R.id.textView1);
			textView.setText(document.getName());
			List<Feature> featureList = document.getFeatureList();
			for (Feature subFeature : featureList) {
				parseFeature(subFeature);
			}
		} else if (feature instanceof Folder) {
			Folder folder = (Folder)feature;
			List<Feature> featureList = folder.getFeatureList();
			for (Feature subFeature : featureList) {
				parseFeature(subFeature);
			}
		} else if (feature instanceof NetworkLink) {
			NetworkLink networkLink = (NetworkLink)feature;
			networkLink.getLink();
			
		} else if (feature instanceof Placemark) {
			Placemark placemark = (Placemark)feature;
			List<Geometry> geoList = placemark.getGeometryList();
			for (Geometry geo : geoList) {
				parseGeometry(geo);
			}
		} else if (feature instanceof GroundOverlay) {
			GroundOverlay groundOverlay = (GroundOverlay)feature;
			groundOverlay.getLatLonQuad();
			
		} else if (feature instanceof PhotoOverlay) {
			PhotoOverlay photoOverlay = (PhotoOverlay)feature;
			photoOverlay.getPoint();
			
		} else if (feature instanceof ScreenOverlay) {
			ScreenOverlay screenOverlay = (ScreenOverlay)feature;
			
		}
	}
	
	private void parseGeometry(Geometry geometry) {
		if (geometry instanceof Point) {
			Point geoPoint = (Point) geometry;
			Coordinate coordinate = geoPoint.getCoordinates();
			OverlayOptions markerOption = new MarkerOptions()
				.position(new LatLng(coordinate.getLatitude(), coordinate.getLongitude()));
			mBaiduMap.addOverlay(markerOption);
		} else if (geometry instanceof LineString) {
			LineString lineString = (LineString) geometry;
			Coordinates coordinates = lineString.getCoordinates();
			List<LatLng> pts = getLatLngsByCoors(coordinates);
			OverlayOptions polylineOptions = new PolylineOptions().color(0xAAFF0000)
				.points(pts);
			mBaiduMap.addOverlay(polylineOptions);
		} else if (geometry instanceof LinearRing) {
			LinearRing linearRing = (LinearRing)geometry;
			Coordinates coordinates = linearRing.getCoordinates();
			List<LatLng> pts = getLatLngsByCoors(coordinates);
			OverlayOptions polygonOption = new PolygonOptions()
		    	.points(pts)
				.stroke(new Stroke(5, 0xAA00FF00)).fillColor(0xAAFFFF00);
			mBaiduMap.addOverlay(polygonOption);
		} else if (geometry instanceof Polygon) {
			Polygon polygon = (Polygon)geometry;
			Boundary innerBoundary = polygon.getInnerBoundaryIs();
			if (innerBoundary != null) {
				parseGeometry(innerBoundary.getLinearRing());
			}
			Boundary outerBoundary = polygon.getOuterBoundaryIs();
			if (outerBoundary != null) {
				parseGeometry(outerBoundary.getLinearRing());
			}
		} else if (geometry instanceof MultiGeometry) {
			MultiGeometry multiGeometry = (MultiGeometry)geometry;
			List<Geometry> geometryList = multiGeometry.getGeometryList();
			for (Geometry geo : geometryList) {
				parseGeometry(geo);
			}
			
		} else if (geometry instanceof MultiTrack) {
			MultiTrack multiTrack = (MultiTrack)geometry;
			List<Track> trackList = multiTrack.getTrackList();
			for (Track track : trackList) {
				parseGeometry(track);
			}
		} else if (geometry instanceof Model) {
			Model model = (Model)geometry;
			//model.getLocation().get
			//TODO: 三维数据暂时没有处理
		} else if (geometry instanceof Track) {
			Track track = (Track)geometry;
			parseGeometry(track.getModel());
			//TODO: 
		}
	}
	
	private List<LatLng> getLatLngsByCoors(Coordinates coordinates) {
		LatLngBounds.Builder builder = new Builder();
		
		List<Coordinate> coordinateList = coordinates.getList();
		
		List<LatLng> pts = new ArrayList<LatLng>(coordinateList.size());
		for (Coordinate coordinate : coordinateList) {
			LatLng pt = new LatLng(coordinate.getLatitude(), coordinate.getLongitude());
			pts.add(pt);
			builder.include(pt);
		}

		LatLngBounds bounds = builder.build();
		MapStatusUpdate u = MapStatusUpdateFactory.newLatLngBounds(bounds);
		mBaiduMap.setMapStatus(u);
		return pts;
	}

	@Override
	protected void onPause() {
		super.onPause();
		// activity 暂停时同时暂停地图控件
		mMapView.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		// activity 恢复时同时恢复地图控件
		mMapView.onResume();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		// activity 销毁时同时销毁地图控件
		mMapView.onDestroy();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(FILE_RESULT_CODE == requestCode){
			Bundle bundle = null;
			if(data!=null && (bundle=data.getExtras()) != null){
				System.out.println(bundle.getString("file"));
			}
		}
	}

	@Override
	public void onGetGeoCodeResult(GeoCodeResult arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onGetReverseGeoCodeResult(ReverseGeoCodeResult result) {
		if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR) {
			Toast.makeText(MainActivity.this, "抱歉，未能找到结果", Toast.LENGTH_LONG)
					.show();
			return;
		}
		
		String strInfo = String.format("纬度：%f 经度：%f; 地址：%s", 
				result.getLocation().latitude, result.getLocation().longitude, result.getAddress());
		
		if (mMarker != null) {
			mMarker.remove();
		}
		MarkerOptions markerOptions = new MarkerOptions().position(result.getLocation())
				.title(strInfo)
				.icon(BitmapDescriptorFactory.fromResource(R.drawable.icon_marka));
		
		mMarker = (Marker)mBaiduMap.addOverlay(markerOptions);
		
		TextView textView = (TextView)findViewById(R.id.textView1);
		textView.setText(strInfo);
	}

}
