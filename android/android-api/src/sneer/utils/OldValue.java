package sneer.utils;

import java.util.*;

import sneer.commons.exceptions.*;
import android.os.*;

public class OldValue implements Parcelable {

	public static OldValue of(Object o) {
		return new OldValue(o);
	}

	final Object value;
	
	OldValue(Object value) {
		this.value = value;
	}
	
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		write(dest, value);
	}

	public static final Parcelable.Creator<OldValue> CREATOR = new Parcelable.Creator<OldValue>() {
		public OldValue createFromParcel(Parcel in) {
			return OldValue.of(read(in));
		}
		
		public OldValue[] newArray(int size) {
			return new OldValue[size];
		}
	};

	public Object get() {
		return value;
	}
	
	@Override
	public String toString() {
		return Type.of(value).name() + "(" + value + ")";
	}
	
	@Override
	public boolean equals(Object o) {
		if (super.equals(o))
			return true;
		if (!(o instanceof OldValue))
			return false;
		return equals(value, ((OldValue)o).value);
	}

	static boolean equals(Object x, Object y) {
		return x == y || (x != null && y != null && x.equals(y));
	}
	
	public static enum Type {
		NULL {
			@Override
			public Object createFromParcel(Parcel in) {
				return null;
			}

			@Override
			public void writeToParcel(Parcel dest, Object value) {
			}
		},
		STRING {
			@Override
			public Object createFromParcel(Parcel in) {
				return in.readString();
			}

			@Override
			public void writeToParcel(Parcel dest, Object value) {
				dest.writeString((String)value);
			}
		},
		KEYWORD {
			@Override
			public Object createFromParcel(Parcel in) {
				throw new NotImplementedYet();
			}

			@Override
			public void writeToParcel(Parcel dest, Object value) {
				throw new NotImplementedYet();
			}
		},
		LONG {
			@Override
			public Object createFromParcel(Parcel in) {
				return in.readLong();
			}

			@Override
			public void writeToParcel(Parcel dest, Object value) {
				dest.writeLong((Long)value);
			}
		},
		MAP {
			@Override
			public Object createFromParcel(Parcel in) {
				int size = in.readInt();
				HashMap<Object, Object> map = new HashMap<Object, Object>(size);
				for (int i = 0; i < size; i++) {
					map.put(read(in), read(in));
				}
				return map;
			}

			@Override
			public void writeToParcel(Parcel dest, Object value) {
				Map<?, ?> map = (Map<?, ?>)value;
				dest.writeInt(map.size());
				for (Map.Entry<?, ?> entry : map.entrySet()) {
					write(dest, entry.getKey());
					write(dest, entry.getValue());
				}
			}
		},
		PARCELABLE {
			@Override
			public Object createFromParcel(Parcel in) {
				return in.readValue(null);
			}

			@Override
			public void writeToParcel(Parcel dest, Object value) {
				dest.writeValue(value);
			}
		},
		;

		public static Type of(Object o) {
			if (o == null)
				return NULL;
			if (o instanceof String)
				return STRING;
			if (o instanceof Long)
				return LONG;
			if (o instanceof Map)
				return MAP;
			return PARCELABLE;
		}

		public abstract Object createFromParcel(Parcel in);

		public abstract void writeToParcel(Parcel dest, Object value);
	}
	
	static Object read(Parcel in) {
		Type type = Type.values()[in.readInt()];
		return type.createFromParcel(in);
		
	}
	
	static void write(Parcel dest, Object value) {
		Type t = Type.of(value);
		dest.writeInt(t.ordinal());
		t.writeToParcel(dest, value);
	}
}
