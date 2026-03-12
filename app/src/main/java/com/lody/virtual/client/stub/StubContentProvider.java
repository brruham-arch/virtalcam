package com.lody.virtual.client.stub;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

public class StubContentProvider extends ContentProvider {

    public static class C0 extends StubContentProvider {}
    public static class C1 extends StubContentProvider {}
    public static class C2 extends StubContentProvider {}
    public static class C3 extends StubContentProvider {}
    public static class C4 extends StubContentProvider {}
    public static class C5 extends StubContentProvider {}
    public static class C6 extends StubContentProvider {}
    public static class C7 extends StubContentProvider {}
    public static class C8 extends StubContentProvider {}
    public static class C9 extends StubContentProvider {}

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
