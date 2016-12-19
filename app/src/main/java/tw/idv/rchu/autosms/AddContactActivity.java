
package tw.idv.rchu.autosms;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TabHost;

import java.util.HashMap;
import java.util.HashSet;

import tw.idv.rchu.autosms.R;

public class AddContactActivity extends ActionBarActivity {
    static final String TAG = "AddContactActivity";
    static final String EXTRA_BIRTHDAY_MODE = "BitrhdayMode";

    private TabHost mTabHost;
    private TabManager mTabManager;

    /**
     * This is a helper class that implements a generic mechanism for
     * associating fragments with the tabs in a tab host. It relies on a trick.
     * Normally a tab host has a simple API for supplying a View or Intent that
     * each tab will show. This is not sufficient for switching between
     * fragments. So instead we make the content part of the tab host 0dp high
     * (it is not shown) and the TabManager supplies its own dummy view to show
     * as the tab content. It listens to changes in tabs, and takes care of
     * switch to the correct fragment shown in a separate content area whenever
     * the selected tab changes.
     */
    public static class TabManager implements TabHost.OnTabChangeListener {
        private final FragmentActivity mActivity;
        private final TabHost mTabHost;
        private final int mContainerId;
        private final HashMap<String, TabInfo> mTabs = new HashMap<String, TabInfo>();
        TabInfo mLastTab;

        static final class TabInfo {
            private final String tag;
            private final Class<?> clss;
            private final Bundle args;
            private Fragment fragment;

            TabInfo(String _tag, Class<?> _class, Bundle _args) {
                tag = _tag;
                clss = _class;
                args = _args;
            }
        }

        static class DummyTabFactory implements TabHost.TabContentFactory {
            private final Context mContext;

            public DummyTabFactory(Context context) {
                mContext = context;
            }

            @Override
            public View createTabContent(String tag) {
                View v = new View(mContext);
                v.setMinimumWidth(0);
                v.setMinimumHeight(0);
                return v;
            }
        }

        public TabManager(FragmentActivity activity, TabHost tabHost, int containerId) {
            mActivity = activity;
            mTabHost = tabHost;
            mContainerId = containerId;
            mTabHost.setOnTabChangedListener(this);
        }

        public void addTab(TabHost.TabSpec tabSpec, Class<?> clss, Bundle args) {
            tabSpec.setContent(new DummyTabFactory(mActivity));
            String tag = tabSpec.getTag();

            TabInfo info = new TabInfo(tag, clss, args);

            // Check to see if we already have a fragment for this tab, probably
            // from a previously saved state. If so, deactivate it, because our
            // initial state is that a tab isn't shown.
            info.fragment = mActivity.getSupportFragmentManager().findFragmentByTag(tag);
            if (info.fragment != null && !info.fragment.isDetached()) {
                FragmentTransaction ft = mActivity.getSupportFragmentManager().beginTransaction();
                ft.detach(info.fragment);
                ft.commit();
            }

            mTabs.put(tag, info);
            mTabHost.addTab(tabSpec);
        }

        @Override
        public void onTabChanged(String tabId) {
            Log.d(TAG, "onTabChanged:" + tabId);
            
            TabInfo newTab = mTabs.get(tabId);
            if (mLastTab != newTab) {
                FragmentTransaction ft = mActivity.getSupportFragmentManager().beginTransaction();
                if (mLastTab != null) {
                    if (mLastTab.fragment != null) {
                        ft.detach(mLastTab.fragment);
                    }
                }
                if (newTab != null) {
                    if (newTab.fragment == null) {
                        newTab.fragment = Fragment.instantiate(mActivity,
                                newTab.clss.getName(), newTab.args);
                        ft.add(mContainerId, newTab.fragment, newTab.tag);
                    } else {
                        ft.attach(newTab.fragment);
                    }
                }
                
                // Deliver the selected contacts.
                HashSet<Long> contacts = null;
                if ((mLastTab != null) && (mLastTab.fragment != null)) {
                    if (mLastTab.tag == "contacts") {
                        contacts = ((SelectContactFragment) mLastTab.fragment).getContacts();
                    } else if (mLastTab.tag == "groups") {
                        contacts = ((SelectGroupFragment) mLastTab.fragment).getContacts();                    
                    }
                }

                if ((contacts != null) && (newTab != null) && (newTab.fragment != null)) {
                    if (newTab.tag == "contacts") {
                        ((SelectContactFragment) newTab.fragment).setContacts(contacts);
                    } else if (newTab.tag == "groups") {
                        ((SelectGroupFragment) newTab.fragment).setContacts(contacts);               
                    }
                }

                mLastTab = newTab;
                ft.commit();
                mActivity.getSupportFragmentManager().executePendingTransactions();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_add_contact);
        mTabHost = (TabHost) findViewById(android.R.id.tabhost);
        mTabHost.setup();

        mTabManager = new TabManager(this, mTabHost, R.id.realtabcontent);

        mTabManager.addTab(mTabHost.newTabSpec("contacts").setIndicator(getString(R.string.contacts), getResources().getDrawable(R.drawable.ic_action_social_person)),
                SelectContactFragment.class, null);
        mTabManager.addTab(mTabHost.newTabSpec("groups").setIndicator(getString(R.string.groups), getResources().getDrawable(R.drawable.ic_action_social_group)),
                SelectGroupFragment.class, null);

        if (savedInstanceState != null) {
            mTabHost.setCurrentTabByTag(savedInstanceState.getString("tab"));
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onSelectOkButtonClicked(View view) {
        String tabTag = mTabHost.getCurrentTabTag();
        HashSet<Long> selectedContacts = null;
        int resultCode = RESULT_CANCELED;

        // Get the selected contacts.
        Fragment f = getSupportFragmentManager().findFragmentByTag(tabTag);
        if (tabTag == "contacts") {
            selectedContacts = ((SelectContactFragment) f).getContacts();
            resultCode = RESULT_OK;
        } else if (tabTag == "groups") {
            selectedContacts = ((SelectGroupFragment) f).getContacts();
            resultCode = RESULT_OK;
        }

        // Return the selected contacts.
        Intent intent = new Intent();
        intent.putExtra(WriteSmsActivity.EXTRA_SELECTED_CONTACT_DATA_IDS, selectedContacts);
        setResult(resultCode, intent);
        finish();
    }

    public void onSelectCancelButtonClicked(View view) {
        // Do nothing.
        setResult(RESULT_CANCELED, null);
        finish();
    }
}
