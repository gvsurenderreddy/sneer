package sneer.android.main.ui;

import static sneer.android.main.SneerApp.*;
import static sneer.android.ui.SneerActivity.*;

import java.io.*;
import java.util.*;

import rx.*;
import rx.android.schedulers.*;
import rx.functions.*;
import sneer.*;
import sneer.Message;
import sneer.android.main.*;
import sneer.android.main.ui.MainActivity.EmbeddedOptions;
import sneer.commons.*;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnClickListener;
import android.widget.*;
import android.widget.PopupMenu.OnDismissListener;

public class ConversationActivity extends Activity {

	static final String PARTY_PUK = "partyPuk";
	static final String ACTIVITY_TITLE = "activityTitle";

	private static final Comparator<? super Message> BY_TIMESTAMP = new Comparator<Message>() { @Override public int compare(Message lhs, Message rhs) {
		return Comparators.compare(lhs.timestampCreated(), rhs.timestampCreated());
	}};

	private final List<Message> messages = new ArrayList<Message>();
	private ConversationAdapter adapter;

	private ActionBar actionBar;
	private EmbeddedOptions embeddedOptions;
	private Party party;
	private Conversation conversation;

	private ImageButton actionButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_conversation);
		
		actionBar = getActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
		actionBar.setHomeButtonEnabled(true);
		
		embeddedOptions = (EmbeddedOptions) getIntent().getExtras().getSerializable("embeddedOptions");
		
		party = sneer().produceParty((PublicKey)getIntent().getExtras().getSerializable(PARTY_PUK));
		
		plugActionBarTitle(actionBar, party.name());
		
		sneer().profileFor(party).selfie().observeOn(AndroidSchedulers.mainThread()).cast(byte[].class).subscribe(new Action1<byte[]>() { @Override public void call(byte[] selfie) {
			actionBar.setIcon((Drawable)new BitmapDrawable(getResources(), BitmapFactory.decodeByteArray(selfie, 0, selfie.length)));
		}});

		conversation = sneer().produceConversationWith(party);
		conversation.unreadMessageCountReset();

		adapter = new ConversationAdapter(this,
			this.getLayoutInflater(),
			R.layout.list_item_user_message,
			R.layout.list_item_party_message,
			messages,
			party);

		deferUI(conversation.messages()).subscribe(new Action1<List<Message>>() { @Override public void call(List<Message> msgs) {
			messages.clear();
			messages.addAll(msgs);
			adapter.notifyDataSetChanged();
		}});

		ListView listView = (ListView) findViewById(R.id.listView);
		listView.setAdapter(adapter);

		final TextView editText = (TextView) findViewById(R.id.editText);
		editText.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (editText.getText().toString().trim() != null && !editText.getText().toString().trim().isEmpty())
					actionButton.setImageResource(R.drawable.ic_action_send);
				else
					actionButton.setImageResource(R.drawable.ic_action_new);
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void afterTextChanged(Editable s) {}
		});
		
		actionButton = (ImageButton)findViewById(R.id.actionButton);
		actionButton.setImageResource(R.drawable.ic_action_new);
		actionButton.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) {
			handleClick(editText.getText().toString().trim());
			editText.setText("");
		}

		private void handleClick(String text) {
			if (!text.isEmpty())
				conversation.sendMessage(text);
			else
				openIteractionMenu();
		}

		private void openIteractionMenu() {
			final PopupMenu menu = new PopupMenu(ConversationActivity.this, actionButton);
			
	
			final Subscription s = conversation.menu().observeOn(AndroidSchedulers.mainThread()).subscribe(new Action1<List<ConversationMenuItem>>() {  @SuppressWarnings("deprecation")
			@Override public void call(List<ConversationMenuItem> menuItems) {
				menu.getMenu().close();
				menu.getMenu().clear();
				for (final ConversationMenuItem item : menuItems) {
					menu.getMenu().add(item.caption()).setOnMenuItemClickListener(new OnMenuItemClickListener() { @Override public boolean onMenuItemClick(MenuItem ignored) {
						menu.getMenu().close();
						item.call(party.publicKey().current());
						return true;
					}}).setIcon(new BitmapDrawable(BitmapFactory.decodeStream(new ByteArrayInputStream(item.icon()))));
				}
				menu.show();
			} });
			menu.setOnDismissListener(new OnDismissListener() {  @Override public void onDismiss(PopupMenu menu) {
				s.unsubscribe();
			} });
		}});		
	}
	
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (embeddedOptions.conversationAction == null) {
			return false;
		}
		getMenuInflater().inflate(R.menu.conversation, menu);
		if (embeddedOptions.conversationLabel != null) {
			MenuItem item = menu.findItem(R.id.action_new_conversation);
			item.setTitle(embeddedOptions.conversationLabel);
		}
		return true;
	}

	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_new_conversation:
			launchNewConversation();
			return true;
		case android.R.id.home:
			navigateToProfile();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}


	private void navigateToProfile() {
		Intent intent = new Intent();
		intent.setClass(this, ContactActivity.class);
		intent.putExtra(PARTY_PUK, party.publicKey().current());
		intent.putExtra(ACTIVITY_TITLE, "Contact");
		startActivity(intent);
	}


	private void launchNewConversation() {
		Contact contact = sneer().findContact(party);
		Intent intent = new Intent(embeddedOptions.conversationAction);
		intent.putExtra(SneerAndroid.TYPE, embeddedOptions.type);
		intent.putExtra("contactNickname", contact.nickname().current());
		intent.putExtra(SneerAndroid.PARTY_PUK, party.publicKey().current());
		startActivity(intent);
	}
	
	
	@SuppressWarnings("unused")
	private void onMessage(Message msg) {
		int insertionPointHint = Collections.binarySearch(messages, msg, BY_TIMESTAMP);
		if (insertionPointHint < 0) {
			int insertionPoint = Math.abs(insertionPointHint) - 1;
			messages.add(insertionPoint, msg);
			adapter.notifyDataSetChanged();
		}
	}
	
}
