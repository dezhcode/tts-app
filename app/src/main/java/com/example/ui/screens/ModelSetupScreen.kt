package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.ModelInventory
import com.example.ui.ModelEngineState
import java.util.Locale

@Composable
fun ModelSetupSheetContent(
    inventory: ModelInventory,
    modelState: ModelEngineState,
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("مشخصات مدل گفتار Avina", style = MaterialTheme.typography.titleLarge)
        Text(
            "موتور تبدیل متن فارسی به گفتار، با اجرای محلی و بدون نیاز به اینترنت هنگام تولید صدا.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val status = when (modelState) {
                    is ModelEngineState.Ready -> "آماده تولید گفتار"
                    is ModelEngineState.Loading -> "در حال راه‌اندازی موتور"
                    is ModelEngineState.Error -> "خطا در راه‌اندازی موتور"
                    is ModelEngineState.Missing -> "فایل‌های مدل ناقص هستند"
                    else -> "در حال بررسی مدل"
                }
                Text("وضعیت: $status", style = MaterialTheme.typography.titleMedium)
                if (modelState is ModelEngineState.Error) {
                    Text(modelState.message, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Text("اطلاعات فنی", style = MaterialTheme.typography.titleMedium)
        val details = listOf(
            "نام محصول" to "Avina",
            "مدل پایه" to "Cilla Mini",
            "شناسه مدل" to "mehdi-hf/cilla-mini",
            "زبان" to "فارسی",
            "کاربرد" to "تبدیل متن به گفتار (TTS)",
            "تعداد پارامترها" to "حدود ۱۸.۲ میلیون",
            "معماری تولید" to "FlowLM، هشت لایه",
            "رمزگشای صوت" to "Mimi",
            "موتور اجرا" to "Phonon / WebAssembly",
            "نرخ نمونه‌برداری" to "۲۴٬۰۰۰ هرتز",
            "فرمت وزن‌ها" to "Safetensors",
            "حالت پردازش" to "آفلاین، روی دستگاه",
            "مجوز مدل" to "CC BY-NC 4.0 (غیرتجاری)",
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                details.forEachIndexed { index, (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 12.dp))
                    }
                    if (index != details.lastIndex) HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        Text("فایل‌های مدل", style = MaterialTheme.typography.titleMedium)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ) {
            Column(modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                inventory.files.forEach { file ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (file.exists) Icons.Rounded.CheckCircle
                                else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (file.exists) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                        )
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(file.name, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f))
                        }
                        Text(if (file.exists) "موجود" else "ناموجود",
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Text(
            "اندازه وزن‌های مدل در نسخه Phonon حدود ۱۰۷ مگابایت است. اندازه APK شامل موتور اجرا و فایل‌های اندروید نیز می‌شود.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
    }
}
