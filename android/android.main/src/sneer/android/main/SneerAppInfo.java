package sneer.android.main;

import java.io.*;
import java.util.*;

import rx.*;
import rx.Observable;
import rx.functions.*;
import rx.observers.*;
import sneer.*;
import sneer.tuples.*;
import android.content.*;
import android.content.pm.*;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.*;
import android.util.*;

public class SneerAppInfo implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private static final int PACKAGE_INFO_FLAGS = PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA;

	enum HandlerType {
		SESSION
	}
	
	String packageName;
	String activityName;

	SneerAppInfo.HandlerType handler;
	String type;
	String label;
	int icon;

	public SneerAppInfo(String packageName, String activityName, SneerAppInfo.HandlerType handler, String type, String label, int icon) {
		this.packageName = packageName;
		this.activityName = activityName;
		this.handler = handler;
		this.type = type;
		this.label = label;
		this.icon = icon;
	}

	@Override
	public String toString() {
		return "SneerAppInfo [" + label + ", " + type + "]";
	}

	public static Func1<ActivityInfo, SneerAppInfo> FROM_ACTIVITY = new Func1<ActivityInfo, SneerAppInfo>() {  @Override public SneerAppInfo call(ActivityInfo activityInfo) {
		Bundle meta = activityInfo.metaData;
		return new SneerAppInfo(
				activityInfo.packageName, 
				activityInfo.name, 
				HandlerType.valueOf(meta.getString("sneer:handler")), 
				meta.getString("sneer:type"),
				meta.getString("sneer:label"),
				meta.getInt("sneer:icon"));
	} };
	
	public static Observable<ActivityInfo> filterSneerApps(Observable<PackageInfo> packageInfos) {
		return packageInfos.filter(new Func1<PackageInfo, Boolean>() {  @Override public Boolean call(PackageInfo t1) {
			return t1.activities != null;
		} })
		.flatMap(new Func1<PackageInfo, Observable<ActivityInfo>>() {  @Override public Observable<ActivityInfo> call(PackageInfo packageInfo) {
			return Observable.from(packageInfo.activities);
		} })
		.filter(new Func1<ActivityInfo, Boolean>() {  @Override public Boolean call(ActivityInfo t1) {
			return t1.metaData != null && t1.metaData.getString("sneer:type") != null;
		} });
	}
	
	public static void checkPackages(Context context) {
		Log.i(SneerAppInfo.class.getSimpleName(), "Searching for Sneer apps...");
		
		List<PackageInfo> packages = context.getPackageManager().getInstalledPackages(PACKAGE_INFO_FLAGS);
		
		final SneerAndroid sneer = new SneerAndroid(context);
		
		filterSneerApps(Observable.from(packages))
			.map(FROM_ACTIVITY)
			.toList()
			.subscribe(new Action1<List<SneerAppInfo>>() {  @Override public void call(List<SneerAppInfo> t1) {
				sneer
					.tupleSpace()
					.publisher()
					.type("sneer/apps")
					.pub(t1);
			} });
		
		// TODO: dispose sneer
		
		Log.i(SneerAppInfo.class.getSimpleName(), "Done.");
	}

	public static void packageAdded(Context context, String packageName) {
		try {
			log("Package added: " + packageName);
			PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName, PACKAGE_INFO_FLAGS);
			
			final SneerAndroid sneer = new SneerAndroid(context);
			
			filterSneerApps(Observable.just(packageInfo))
				.map(FROM_ACTIVITY)
				.concatWith(currentKnownApps(sneer))
				.distinct(new Func1<SneerAppInfo, String>() {  @Override public String call(SneerAppInfo t1) {
					return t1.packageName + ":"+t1.activityName;
				}})
				.toList()
				.subscribe(appsListPublisher(sneer));

			// TODO: dispose sneer

		} catch (NameNotFoundException e) {
			throw new RuntimeException(e);
		}
		
	}

	private static void log(String message) {
		Log.i(SneerAppInfo.class.getSimpleName(), message);
	}

	public static void packageRemoved(Context context, final String packageName) {
			
		log("Package removed: " + packageName);
		
		final SneerAndroid sneer = new SneerAndroid(context);
		
		currentKnownApps(sneer)
			.filter(new Func1<SneerAppInfo, Boolean>() {  @Override public Boolean call(SneerAppInfo t1) {
				log("---> " + t1.packageName + " - " + packageName + " - " + !t1.packageName.equals(packageName));
				return !t1.packageName.equals(packageName);
			} })
			.toList()
			.subscribe(appsListPublisher(sneer));

		// TODO: dispose sneer
	}

	private static Action1<List<SneerAppInfo>> appsListPublisher(final SneerAndroid sneer) {
		return new Action1<List<SneerAppInfo>>() {  @Override public void call(List<SneerAppInfo> t1) {
			log("Pushing new app list: " + t1);
			sneer
				.tupleSpace()
				.publisher()
				.type("sneer/apps")
				.pub(t1);
		} };
	}

	@SuppressWarnings("rawtypes")
	private static Observable<SneerAppInfo> currentKnownApps(SneerAndroid sneer) {
		return sneer.tupleSpace().filter().type("sneer/apps").localTuples()
			.map(Tuple.TO_PAYLOAD)
			.cast(List.class)
			.lastOrDefault(new ArrayList())
			.flatMap(new Func1<List, Observable<SneerAppInfo>>() {  @SuppressWarnings("unchecked") @Override public Observable<SneerAppInfo> call(List t1) {
				log("Current sneer apps: " + t1);
				return Observable.from(t1);
			} });
	}
}