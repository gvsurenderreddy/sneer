package sneer.android.ipc;

import static sneer.TupleSpaceFactoryClient.SubscriptionOp.ON_COMPLETED;
import static sneer.TupleSpaceFactoryClient.SubscriptionOp.ON_NEXT;
import static sneer.android.SneerAndroidSingleton.sneer;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import rx.Observable;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import sneer.TupleSpaceFactoryClient;
import sneer.commons.InteractiveSerializer;
import sneer.tuples.Tuple;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.ResultReceiver;

public class TupleSpaceService extends Service {

	private final AtomicInteger nextSubscriptionId = new AtomicInteger();
	private final ConcurrentMap<Integer, Subscription> subscriptions = new ConcurrentHashMap<Integer, Subscription>();

	@SuppressWarnings("unused")
	private String friendlyErrorMessage; //Return this to the callers. See how it is set, below.

	private final InteractiveSerializer serializer = new InteractiveSerializer();


	@SuppressWarnings("unchecked")
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);

		if (intent == null || !intent.hasExtra("op"))
			return START_STICKY;

		switch(TupleSpaceFactoryClient.TupleSpaceOp.values()[intent.getIntExtra("op", -1)]) {
		case PUBLISH:
			publish((Map<String, Object>)serializer.deserialize(intent.getByteArrayExtra("tuple")));
			break;
		case SUBSCRIBE:
			subscribe((Map<String, Object>)serializer.deserialize(intent.getByteArrayExtra("criteria")), (ResultReceiver)intent.getParcelableExtra("resultReceiver"));
			break;
		case SUBSCRIBE_LOCAL:
			subscribeLocal((Map<String, Object>)serializer.deserialize(intent.getByteArrayExtra("criteria")), (ResultReceiver)intent.getParcelableExtra("resultReceiver"));
			break;
		case UNSUBSCRIBE:
			unsubscribe(intent.getIntExtra("subscription", -1));
			break;
		default:
			break;
		}

		return START_STICKY;
	}


	private void unsubscribe(int subscriptionId) {
		if (subscriptionId == -1)
			throw new IllegalArgumentException("Unknown subscription: " + subscriptionId);

		Subscription s = subscriptions.remove(subscriptionId);

		if (s != null)
			s.unsubscribe();
	}


	private void subscribe(Map<String, Object> criteria, ResultReceiver resultReceiver) {
		subscribe(resultReceiver, sneer().tupleSpace().filter().putFields(criteria).tuples());
	}


	private void subscribeLocal(Map<String, Object> criteria, ResultReceiver resultReceiver) {
		subscribe(resultReceiver, sneer().tupleSpace().filter().putFields(criteria).localTuples());
	}


	private void subscribe(final ResultReceiver resultReceiver, Observable<Tuple> tuples) {
		int id = nextSubscriptionId.getAndIncrement();
		resultReceiver.send(TupleSpaceFactoryClient.SubscriptionOp.SUBSCRIPTION_ID.ordinal(), bundle(id));

		Subscription s = tuples.doOnCompleted(new Action0() { @Override public void call() {
			resultReceiver.send(ON_COMPLETED.ordinal(), new Bundle());
		}}).subscribe(new Action1<Tuple>() { @Override public void call(Tuple t1) {
			resultReceiver.send(ON_NEXT.ordinal(), bundle(serializer.serialize(new HashMap<String, Object>(t1))));
		}});

		subscriptions.put(id, s);
	}


	private Bundle bundle(Object value) {
		Bundle ret = new Bundle();
		if (value instanceof Parcelable)
			ret.putParcelable("value", (Parcelable) value);
		else
			ret.putSerializable("value", (Serializable) value);

		return ret;
	}


	private void publish(Map<String, Object> tuple) {
		sneer().tupleSpace().publisher().putFields(tuple).pub();
	}


	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}


	public static void startTupleSpaceService(Context context) {
		Intent startServiceIntent = new Intent(context, TupleSpaceService.class);
	    context.startService(startServiceIntent);
	}

}