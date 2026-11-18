# ربط تحليل Waze بمكونات IRAQ WAZ

| المؤشر المرصود في وثيقة APK | وحدة IRAQ WAZ المقابلة | حالة التنفيذ الحالية |
|---|---|---|
| MapNativeManager / map clicked, dragged | map | خريطة أساسية مضافة؛ التفاعلات المتقدمة تحتاج اختبار |
| LocationNativeManager / onPosition | location | إذن الموقع وآخر موقع معروف فقط |
| NavigationInfo / current instruction | navigation | لم تنفذ |
| Route changed / offline route changed | routing | لم ينفذ |
| ETA seconds / distance / minutes | routing + navigation | لم ينفذ |
| Traffic update / jams alerts | traffic | لم ينفذ؛ لا توجد بيانات حية |
| SearchNativeManager / category search | search | لم ينفذ |
| Gas stations / price update | places/fuel | لم ينفذ |
| Places / reverse geocode | places + search | لم ينفذ |
| Favorites | saved places | لم ينفذ |
| Parking search / parked here | parking | لم ينفذ |
| Report feedback / thumbs up-down | reports | مخطط فقط |
| Road closure / danger zone | reports + moderation | مخطط فقط |
| TTS player / voice download | voice guidance | لم ينفذ |
| Auth / login / logout | account | لم ينفذ |
| Inbox | notifications | لم ينفذ |

ملاحظة: أسماء JNI والرموز داخل APK مؤشرات على واجهات/مسؤوليات داخلية، ولا تثبت وحدها أن كل وظيفة متاحة أو تعمل في العراق. هذه المصفوفة لا تدّعي استنساخ منطق Waze.
