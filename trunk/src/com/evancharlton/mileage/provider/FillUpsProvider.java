
package com.evancharlton.mileage.provider;

import com.evancharlton.mileage.SettingsActivity;
import com.evancharlton.mileage.dao.Dao;
import com.evancharlton.mileage.provider.backup.BackupTransport;
import com.evancharlton.mileage.provider.backup.FileBackupTransport;
import com.evancharlton.mileage.provider.tables.CacheTable;
import com.evancharlton.mileage.provider.tables.ContentTable;
import com.evancharlton.mileage.provider.tables.FieldsTable;
import com.evancharlton.mileage.provider.tables.FillupsFieldsTable;
import com.evancharlton.mileage.provider.tables.FillupsTable;
import com.evancharlton.mileage.provider.tables.ServiceIntervalTemplatesTable;
import com.evancharlton.mileage.provider.tables.ServiceIntervalsTable;
import com.evancharlton.mileage.provider.tables.VehicleTypesTable;
import com.evancharlton.mileage.provider.tables.VehiclesTable;
import com.evancharlton.mileage.util.Debugger;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseIntArray;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Exposed URIs:
 * <ul>
 * <li>fillups/</li>
 * <li>fillups/#</li>
 * <li>fillups/fields</li>
 * <li>fillups/fields/#</li>
 * <li>fillups/field/#</li>
 * <li>fields/</li>
 * <li>fields/#</li>
 * <li>vehicles/</li>
 * <li>vehicles/#</li>
 * <li>vehicles/types/</li>
 * <li>vehicles/types/#</li>
 * <li>intervals/</li>
 * <li>intervals/#</li>
 * <li>intervals/templates</li>
 * <li>intervals/templates/#</li>
 * <li>cache</li>
 * <li>cache/*</li>
 * </ul>
 */
public class FillUpsProvider extends ContentProvider {

    public static final String AUTHORITY = "com.evancharlton.mileage";
    public static final Uri BASE_URI = Uri.parse("content://" + AUTHORITY);
    public static final int DATABASE_VERSION = 7;
    public static final ArrayList<ContentTable> TABLES = new ArrayList<ContentTable>();
    private static final SparseIntArray LOOKUP = new SparseIntArray();

    private static final String DATABASE_NAME = "mileage.db";
    private static final HashMap<String, BackupTransport> BACKUPS = new HashMap<String, BackupTransport>();
    private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    private static final String TAG = "FillupsProvider";

    private DatabaseHelper mDatabaseHelper;

    private final Handler mHandler = new Handler();

    private Runnable mBackupRunnable;

    static {
        TABLES.add(new FillupsTable());
        TABLES.add(new FillupsFieldsTable());
        TABLES.add(new FieldsTable());
        TABLES.add(new VehiclesTable());
        TABLES.add(new VehicleTypesTable());
        TABLES.add(new ServiceIntervalsTable());
        TABLES.add(new ServiceIntervalTemplatesTable());
        TABLES.add(new CacheTable());

        for (ContentTable table : TABLES) {
            table.registerUris();
        }

        putBackup(new FileBackupTransport());
    }

    public static void registerUri(ContentTable table, String path, int code) {
        // TODO(3.1) - Could this code be auto-generated?
        URI_MATCHER.addURI(AUTHORITY, path, code);
        int position = TABLES.indexOf(table);
        if (position < 0) {
            TABLES.add(table);
            position = TABLES.size() - 1;
        }
        LOOKUP.put(code, position);
    }

    private static void putBackup(BackupTransport transport) {
        BACKUPS.put(transport.getClass().getName(), transport);
    }

    public static BackupTransport getBackupTransport(String packageName) {
        return BACKUPS.get(packageName);
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            Log.d(TAG, "Creating database");
            for (ContentTable table : TABLES) {
                try {
                    String sql = table.create();
                    if (sql != null) {
                        db.execSQL(sql);
                    }
                } catch (IllegalArgumentException e) {
                    Log.e("DatabaseHelper", "Could not create table", e);
                } catch (IllegalAccessException e) {
                    Log.e("DatabaseHelper", "Could not create table", e);
                }
            }

            initTables(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, final int oldVersion, final int newVersion) {
            DatabaseUpgrader.upgradeDatabase(db);
        }
    }

    public static ArrayList<BackupTransport> getBackupTransports() {
        return new ArrayList<BackupTransport>(BACKUPS.values());
    }

    public static void initTables(SQLiteDatabase db) {
        for (ContentTable table : TABLES) {
            String[] init = table.init(false);
            if (init != null) {
                for (String sql : init) {
                    db.execSQL(sql);
                }
            }
        }
    }

    @Override
    public boolean onCreate() {
        mDatabaseHelper = new DatabaseHelper(getContext());

        // Save the path to the database into SharedPreferences
        SharedPreferences prefs = getContext().getSharedPreferences(Settings.NAME,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String path = mDatabaseHelper.getReadableDatabase().getPath();
        Log.d(TAG, "Storing database path as " + path);
        editor.putString(Settings.DATABASE_PATH, path);
        editor.commit();

        URI_MATCHER.addURI(AUTHORITY, "reset/", 0);
        return true;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        Debugger.checkQueryOnUiThread(getContext());
        SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();

        int count = -1;
        final int type = URI_MATCHER.match(uri);
        int position = LOOKUP.get(type, -1);
        if (position >= 0) {
            count = TABLES.get(position).delete(db, uri, selection, selectionArgs);
        }

        if (count < 0) {
            throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        notifyListeners(uri);
        return count;
    }

    @Override
    public String getType(Uri uri) {
        final int type = URI_MATCHER.match(uri);
        if (type == 0) {
            // TODO(3.1) - Figure this out.
            // mDatabaseHelper.close();
            // mDatabaseHelper.getReadableDatabase();
            // Log.d(TAG, "Database closed!");
            return null;
        }
        String result = null;
        for (ContentTable table : TABLES) {
            result = table.getType(type);
            if (result != null) {
                return result;
            }
        }
        throw new IllegalArgumentException("Unknown URI: " + uri);
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        Debugger.checkQueryOnUiThread(getContext());
        final int match = URI_MATCHER.match(uri);
        long newId = -1L;
        SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
        int position = LOOKUP.get(match, -1);
        if (position >= 0) {
            initialValues.put(Dao.TIMESTAMP, System.currentTimeMillis());
            newId = TABLES.get(position).insert(match, db, initialValues);
            if (newId >= 0) {
                uri = ContentUris.withAppendedId(uri, newId);
                notifyListeners(uri);
            }
            return uri;
        }
        throw new IllegalArgumentException("Unknown URI: " + uri);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        Debugger.checkQueryOnUiThread(getContext());
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        final int match = URI_MATCHER.match(uri);
        boolean changed = false;
        ContentTable queryTable = null;
        int position = LOOKUP.get(match, -1);
        if (position >= 0) {
            ContentTable table = TABLES.get(position);
            changed = table.query(match, uri, qb, getContext(), projection);
            if (changed) {
                queryTable = table;
            }
        }

        // TODO(3.1) - Clean this up
        if (!changed) {
            throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        if (projection == null) {
            projection = queryTable.getProjection();
        }

        String orderBy = TextUtils.isEmpty(sortOrder) ? queryTable.getDefaultSortOrder()
                : sortOrder;

        SQLiteDatabase db;
        try {
            db = mDatabaseHelper.getReadableDatabase();
        } catch (SQLiteException e) {
            db = mDatabaseHelper.getWritableDatabase();
        }

        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);
        c.setNotificationUri(getContext().getContentResolver(), uri);

        return c;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        Debugger.checkQueryOnUiThread(getContext());
        final int match = URI_MATCHER.match(uri);
        int position = LOOKUP.get(match, -1);
        if (position >= 0) {
            SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
            values.put(Dao.TIMESTAMP, System.currentTimeMillis());
            int count = TABLES.get(position).update(match, db, uri, values, selection,
                    selectionArgs);
            if (count >= 0) {
                notifyListeners(uri);
            }
            return count;
        }
        throw new IllegalArgumentException("Unknown URI: " + uri);
    }

    private void notifyListeners(final Uri uri) {
        Context context = getContext();
        context.getContentResolver().notifyChange(uri, null);

        mHandler.removeCallbacks(mBackupRunnable);

        mBackupRunnable = new Runnable() {
            @Override
            public void run() {
                SharedPreferences preferences = getContext().getSharedPreferences(
                        SettingsActivity.NAME, Context.MODE_PRIVATE);
                for (BackupTransport transport : BACKUPS.values()) {
                    if (transport.isEnabled(preferences)) {
                        transport.performIncrementalBackup(getContext(), uri);
                    }
                }
            }
        };
        mHandler.postDelayed(mBackupRunnable, 1000);
    }
}
