package app.roadmemory.car.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import app.roadmemory.car.family.FamilyLink

/**
 * 디버그 전용: 앱이 켜질 때(Application보다 먼저) 가족 위치 연결을 이어 둬요. 연결 설정이 저장돼 있을 때만 이어요.
 * 제출용(release) 빌드에는 이 클래스가 없어서 [FamilyLink.source]가 비어 있고, 가족 위치 칸은 그려지지 않아요.
 */
class FamilyInit : ContentProvider() {
    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        connect(ctx)
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        /** 설정이 있으면 읽는 쪽과 내비 쪽을 이어요. 없으면 비워 둬요(가족 위치 칸이 사라져요). */
        fun connect(ctx: android.content.Context) {
            if (FamilyConfig.load(ctx) != null) {
                FamilyLink.source = PositionSource(ctx.applicationContext)
                FamilyLink.router = NaviRoute(ctx.applicationContext)
            } else {
                FamilyLink.source = null
                FamilyLink.router = null
            }
        }
    }
}
