
package tw.idv.rchu.autosms;

import android.content.ContentResolver;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import tw.idv.rchu.autosms.DBInternalHelper.ScheduleRepeat;

import java.util.Calendar;
import java.util.List;
import java.util.Set;

public class MainUtility {

    public static Calendar renewScheduleDatetime(ContentResolver resolver, int repeat,
            Set<Long> phoneDataIds, Calendar dateTime, Calendar nextTime) {
        if (repeat == ScheduleRepeat.DAILY) {
            dateTime.set(nextTime.get(Calendar.YEAR), nextTime.get(Calendar.MONTH),
                    nextTime.get(Calendar.DATE));
            if (dateTime.before(nextTime)) {
                // Plus one day.
                dateTime.add(Calendar.DATE, 1);
            }
        } else if (repeat == ScheduleRepeat.WEEKLY) {
            int dayOfWeek = dateTime.get(Calendar.DAY_OF_WEEK);
            dateTime.set(nextTime.get(Calendar.YEAR), nextTime.get(Calendar.MONTH),
                    nextTime.get(Calendar.DATE));
            dateTime.getTimeInMillis(); // Force to recompute for fixing next week issue.
            dateTime.set(Calendar.DAY_OF_WEEK, dayOfWeek);
            if (dateTime.before(nextTime)) {
                // Plus 7 days.
                dateTime.add(Calendar.DATE, 7);
            }
        } else if (repeat == ScheduleRepeat.MONTHLY) {
            int dayOfMonth = dateTime.get(Calendar.DAY_OF_MONTH);

            if (dateTime.before(nextTime)) {
                // Find a month will not exceed the max day.
                dateTime.set(nextTime.get(Calendar.YEAR), nextTime.get(Calendar.MONTH), 1);
                while (dayOfMonth > dateTime.getActualMaximum(Calendar.DAY_OF_MONTH)) {
                    // Plus one month.
                    dateTime.add(Calendar.MONTH, 1);
                }
                dateTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            }

            if (dateTime.before(nextTime)) {
                // Plus one month.
                dateTime.add(Calendar.MONTH, 1);

                // Find a month will not exceed the max day.
                while (dayOfMonth > dateTime.getActualMaximum(Calendar.DAY_OF_MONTH)) {
                    // Plus one month.
                    dateTime.add(Calendar.MONTH, 1);
                }
                dateTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            }
        } else if (repeat == ScheduleRepeat.YEARLY) {
            // TODO: fix February 29 issue.
            dateTime.set(Calendar.YEAR, nextTime.get(Calendar.YEAR));

            if (dateTime.before(nextTime)) {
                // Plus one year.
                dateTime.add(Calendar.YEAR, 1);
            }
        } else if (repeat == ScheduleRepeat.BIRTHDAY) {
            List<Calendar> birthdays = ContactUtility.getContactBirthdayCalendar(resolver,
                    phoneDataIds, dateTime);

            if (birthdays != null && birthdays.size() > 0) {
                // Find out the nearest birthday.
                boolean bFound = false;
                for (Calendar c : birthdays) {
                    if (c.after(nextTime)) {
                        dateTime = c;
                        bFound = true;
                        break;
                    }
                }
                if (!bFound) {
                    // Plus one year to first birthday.
                    dateTime = birthdays.get(0);
                    dateTime.add(Calendar.YEAR, 1);
                }
            }
        }

        return dateTime;
    }
    
    public static boolean isDebug(ApplicationInfo appInfo) {
        if ((appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) > 0) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean isRelease(ApplicationInfo appInfo) {
        if ((appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            return true;
        } else {
            return false;
        }
    }
    
    public static void onBindView(View view, CharSequence title, CharSequence summary) {
        final TextView titleView = (TextView) view.findViewById(android.R.id.title);
        if (titleView != null) {
            if (title != null && title.length() > 0) {
                titleView.setText(title);
                titleView.setVisibility(View.VISIBLE);
            } else {
                titleView.setVisibility(View.GONE);
            }
        }

        final TextView summaryView = (TextView) view.findViewById(android.R.id.summary);
        if (summaryView != null) {
            if (summary != null && summary.length() > 0) {
                summaryView.setText(summary);
                summaryView.setVisibility(View.VISIBLE);
            } else {
                summaryView.setVisibility(View.GONE);
            }
        }

        final ImageView imageView = (ImageView) view.findViewById(android.R.id.icon);
        if (imageView != null) {
            imageView.setVisibility(View.GONE);
        }        
    }    
    
    public static boolean hasGingerbread() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD;
    }

    public static boolean hasHoneycomb() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
    }

    public static boolean hasHoneycombMR1() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1;
    }

    public static boolean hasHoneycombMR2() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2;
    }

    public static boolean hasIceCreamSandwich() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }

    public static boolean hasJellyBean() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    }    
}
