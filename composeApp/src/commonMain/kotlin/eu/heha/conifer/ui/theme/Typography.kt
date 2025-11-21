package eu.heha.conifer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import conifer.composeapp.generated.resources.Res
import conifer.composeapp.generated.resources.lato_black
import conifer.composeapp.generated.resources.lato_black_italic
import conifer.composeapp.generated.resources.lato_bold
import conifer.composeapp.generated.resources.lato_bold_italic
import conifer.composeapp.generated.resources.lato_italic
import conifer.composeapp.generated.resources.lato_light
import conifer.composeapp.generated.resources.lato_light_italic
import conifer.composeapp.generated.resources.lato_regular
import conifer.composeapp.generated.resources.lato_thin
import conifer.composeapp.generated.resources.lato_thin_italic
import conifer.composeapp.generated.resources.story_script_regular
import org.jetbrains.compose.resources.Font


@Composable
fun appTypography(): Typography {
    val baseline = Typography()
    val displayFontFamily = FontFamily(Font(Res.font.story_script_regular))
    val bodyFontFamily = FontFamily(
        Font(Res.font.lato_regular),
        Font(Res.font.lato_black, weight = FontWeight.Black),
        Font(Res.font.lato_black_italic, weight = FontWeight.Black, style = FontStyle.Italic),
        Font(Res.font.lato_bold, weight = FontWeight.Bold),
        Font(Res.font.lato_bold_italic, weight = FontWeight.Bold, style = FontStyle.Italic),
        Font(Res.font.lato_italic, style = FontStyle.Italic),
        Font(Res.font.lato_light, weight = FontWeight.Light),
        Font(Res.font.lato_light_italic, weight = FontWeight.Light, style = FontStyle.Italic),
        Font(Res.font.lato_thin, weight = FontWeight.Thin),
        Font(Res.font.lato_thin_italic, weight = FontWeight.Thin, style = FontStyle.Italic)

    )
    return Typography(
        displayLarge = baseline.displayLarge.copy(fontFamily = displayFontFamily),
        displayMedium = baseline.displayMedium.copy(fontFamily = displayFontFamily),
        displaySmall = baseline.displaySmall.copy(fontFamily = displayFontFamily),
        headlineLarge = baseline.headlineLarge.copy(fontFamily = displayFontFamily),
        headlineMedium = baseline.headlineMedium.copy(fontFamily = displayFontFamily),
        headlineSmall = baseline.headlineSmall.copy(fontFamily = displayFontFamily),
        titleLarge = baseline.titleLarge.copy(fontFamily = displayFontFamily),
        titleMedium = baseline.titleMedium.copy(fontFamily = displayFontFamily),
        titleSmall = baseline.titleSmall.copy(fontFamily = displayFontFamily),
        bodyLarge = baseline.bodyLarge.copy(fontFamily = bodyFontFamily),
        bodyMedium = baseline.bodyMedium.copy(fontFamily = bodyFontFamily),
        bodySmall = baseline.bodySmall.copy(fontFamily = bodyFontFamily),
        labelLarge = baseline.labelLarge.copy(fontFamily = bodyFontFamily),
        labelMedium = baseline.labelMedium.copy(fontFamily = bodyFontFamily),
        labelSmall = baseline.labelSmall.copy(fontFamily = bodyFontFamily),
    )
}