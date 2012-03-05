package net.everythingandroid.smspopup.ui;

import java.util.List;

import com.commonsware.cwac.wakeful.WakefulIntentService;

import net.everythingandroid.smspopup.R;
import net.everythingandroid.smspopup.provider.SmsPopupContract.ContactNotifications;
import net.everythingandroid.smspopup.service.SmsPopupUtilsService;
import net.everythingandroid.smspopup.util.Log;
import net.everythingandroid.smspopup.util.SmsPopupUtils;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnCreateContextMenuListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AutoCompleteTextView;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class ConfigContactsActivity extends FragmentActivity {
    private static final int REQ_CODE_CHOOSE_CONTACT = 0;

    private static final String[] CONTACT_PROJECTION = new String[] {
            Contacts._ID,
            Contacts.DISPLAY_NAME,
            Contacts.LOOKUP_KEY
    };

    private static final int COLUMN_DISPLAY_NAME = 1;
    private static final int COLUMN_LOOKUP_KEY = 2;

    @SuppressLint("NewApi")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (SmsPopupUtils.isHoneycomb()) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.add(android.R.id.content, new ConfigContactsListFragment());
        ft.commit();

        // Kick off the contact names sync background task.
        Intent i = new Intent(getApplicationContext(), SmsPopupUtilsService.class);
        i.setAction(SmsPopupUtilsService.ACTION_SYNC_CONTACT_NAMES);
        WakefulIntentService.sendWakefulWork(getApplicationContext(), i);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
        case REQ_CODE_CHOOSE_CONTACT:
            if (resultCode == -1) { // Success, contact chosen
                final List<String> segments = data.getData().getPathSegments();
                final String lookupKey = segments.get(segments.size() - 2);
                startActivity(getConfigPerContactIntent(getApplicationContext(),
                        ContactNotifications.buildLookupUri(lookupKey)));
            }
            break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.config_contacts, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            finish();
            break;
        case R.id.add_menu_item:
            startContactPicker();
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void startContactPicker() {
        startActivityForResult(
                new Intent(Intent.ACTION_PICK, Contacts.CONTENT_URI), REQ_CODE_CHOOSE_CONTACT);
    }

    /**
     * Get intent that starts the notification config for a contact - no params means add new (ie.
     * it will prompt for the user to pick a contact).
     * 
     * @return the intent that can be started
     */
    private static Intent getConfigPerContactIntent(Context context) {
        Intent i = new Intent(context, ConfigContactActivity.class);
        return i;
    }

    /**
     * Get intent that starts the notification config for a contact. contactId is the system id of
     * the contact to edit
     * 
     * @return the intent that can be started
     */
    private static Intent getConfigPerContactIntent(Context context, long rowId) {
        Intent i = getConfigPerContactIntent(context);
        i.putExtra(ConfigContactActivity.EXTRA_CONTACT_URI,
                ContactNotifications.buildContactUri(rowId));
        return i;
    }

    private static Intent getConfigPerContactIntent(Context context, Uri uri) {
        Intent i = getConfigPerContactIntent(context);
        i.putExtra(ConfigContactActivity.EXTRA_CONTACT_URI, uri);
        return i;
    }

    public static class ContactListAdapter extends CursorAdapter {
        private ContentResolver mContent;

        public ContactListAdapter(Context context, Cursor c) {
            super(context, c);
            mContent = context.getContentResolver();
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            final LayoutInflater inflater = LayoutInflater.from(context);
            final TextView view =
                    (TextView) inflater.inflate(android.R.layout.simple_dropdown_item_1line,
                            parent, false);
            view.setText(cursor.getString(COLUMN_DISPLAY_NAME));
            return view;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ((TextView) view).setText(cursor.getString(COLUMN_DISPLAY_NAME));
        }

        @Override
        public String convertToString(Cursor cursor) {
            return cursor.getString(COLUMN_DISPLAY_NAME);
        }

        @Override
        public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
            if (getFilterQueryProvider() != null) {
                return getFilterQueryProvider().runQuery(constraint);
            }

            return mContent.query(
                    Uri.withAppendedPath(Contacts.CONTENT_FILTER_URI, (String) constraint),
                    CONTACT_PROJECTION, null, null, null);
        }
    }

    public static class ConfigContactsListFragment extends ListFragment implements
            LoaderCallbacks<Cursor> {

        private SimpleCursorAdapter mContactNotififcationsAdapter;
        private ContactListAdapter mSystemContactsAdapter;

        private static final int CONTEXT_MENU_DELETE_ID = Menu.FIRST;
        private static final int CONTEXT_MENU_EDIT_ID = Menu.FIRST + 1;
        
        private static final int LOADER_CONTACT_NOTIFICATIONS = 0;
        private static final int LOADER_SYSTEM_CONTACTS = 1;

        public ConfigContactsListFragment() {}

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            super.onListItemClick(l, v, position, id);
            startActivity(getConfigPerContactIntent(getActivity(), id));
        }
        
        @Override
        public boolean onContextItemSelected(MenuItem item) {
            AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

            if (Log.DEBUG)
                Log.v("onContextItemSelected()");

            if (info.id != -1) {
                switch (item.getItemId()) {
                case CONTEXT_MENU_EDIT_ID:
                    if (Log.DEBUG)
                        Log.v("Editing contact " + info.id);
                    startActivity(getConfigPerContactIntent(getActivity(), info.id));
                    return true;
                case CONTEXT_MENU_DELETE_ID:
                    if (Log.DEBUG)
                        Log.v("Deleting contact " + info.id);
                    getActivity().getContentResolver().delete(
                            ContactNotifications.buildContactUri(info.id), null,
                            null);
                    return true;
                default:
                    return super.onContextItemSelected(item);
                }
            }
            return false;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {

            final View v = inflater.inflate(R.layout.config_contacts_fragment, container, false);

            final ListView mListView = (ListView) v.findViewById(android.R.id.list);
            mListView.setOnCreateContextMenuListener(new OnCreateContextMenuListener() {
                @Override
                public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
                    menu.add(0, CONTEXT_MENU_EDIT_ID, 0, R.string.contact_customization_edit);
                    menu.add(0, CONTEXT_MENU_DELETE_ID, 0, R.string.contact_customization_remove);
                }
            });

            mSystemContactsAdapter = new ContactListAdapter(getActivity(), null);
            final AutoCompleteTextView contactsAutoComplete =
                    (AutoCompleteTextView) v.findViewById(R.id.ContactsAutoCompleteTextView);
            contactsAutoComplete.setAdapter(mSystemContactsAdapter);

            contactsAutoComplete.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    final Cursor c = (Cursor) mSystemContactsAdapter.getItem(position);
                    final Uri uri =
                            Uri.withAppendedPath(
                                    ContactNotifications.CONTENT_LOOKUP_URI,
                                    c.getString(COLUMN_LOOKUP_KEY));
                    startActivity(getConfigPerContactIntent(getActivity(), uri));
                    contactsAutoComplete.setText("");
                }
            });
            
            final String[] from =
                    new String[] { ContactNotifications.CONTACT_NAME, ContactNotifications.SUMMARY };
            final int[] to = new int[] { android.R.id.text1, android.R.id.text2 };

            // Now create an array adapter and set it to display using our row
            mContactNotififcationsAdapter = new SimpleCursorAdapter(
                    getActivity(), R.layout.simple_list_item_2, null, from, to, 0);
            setListAdapter(mContactNotififcationsAdapter);

            getLoaderManager().initLoader(LOADER_CONTACT_NOTIFICATIONS, null, this);  
            getLoaderManager().initLoader(LOADER_SYSTEM_CONTACTS, null, this);

            return v;
        }

        @Override
        public void onResume() {
            super.onResume();
            getLoaderManager().restartLoader(LOADER_CONTACT_NOTIFICATIONS, null, this);
            getLoaderManager().restartLoader(LOADER_SYSTEM_CONTACTS, null, this);
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            super.onCreateContextMenu(menu, v, menuInfo);
            menu.add(0, CONTEXT_MENU_EDIT_ID, 0, R.string.contact_customization_edit);
            menu.add(0, CONTEXT_MENU_DELETE_ID, 0, R.string.contact_customization_remove);
        }

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        	switch (id) {
        	case LOADER_CONTACT_NOTIFICATIONS:
                return new CursorLoader(getActivity(), ContactNotifications.CONTENT_URI,
                        ContactNotifications.PROJECTION_SUMMARY, null, null, null);
        	case LOADER_SYSTEM_CONTACTS:
        		return new CursorLoader(getActivity(), Contacts.CONTENT_URI, 
        				CONTACT_PROJECTION, null, null, null);
        	}
        	return null;
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        	switch (loader.getId()) {
        	case LOADER_CONTACT_NOTIFICATIONS:
        		mContactNotififcationsAdapter.swapCursor(data);
        		break;
        	case LOADER_SYSTEM_CONTACTS:
        		mSystemContactsAdapter.swapCursor(data);
        		break;
        	}
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
        	switch (loader.getId()) {
        	case LOADER_CONTACT_NOTIFICATIONS:
        		mContactNotififcationsAdapter.swapCursor(null);
        		break;
        	case LOADER_SYSTEM_CONTACTS:
        		mSystemContactsAdapter.swapCursor(null);
        		break;
        	}
        }
    }
}
