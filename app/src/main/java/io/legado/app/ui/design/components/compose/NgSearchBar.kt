package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme

/** 与 View 版 NgSearchBar 对齐的 44dp 搜索框。查询状态由页面持有。 */
@Composable
fun NgSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    searchIcon: Painter = painterResource(R.drawable.ic_search),
    onSearch: (String) -> Unit = {}
) {
    val shape = RoundedCornerShape(22.dp)
    val contentColor = colorResource(R.color.ng_on_surface)
    val secondaryColor = colorResource(R.color.ng_on_surface_variant)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(colorResource(R.color.ng_surface_card))
            .border(0.8.dp, colorResource(R.color.ng_card_stroke), shape),
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(
            color = contentColor,
            fontSize = 15.sp,
            lineHeight = 18.sp
        ),
        cursorBrush = SolidColor(androidx.compose.ui.graphics.Color(NgTheme.colors.primary)),
        keyboardOptions = KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Search
        ),
        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = searchIcon,
                    contentDescription = stringResource(R.string.search),
                    modifier = Modifier.size(22.dp),
                    tint = secondaryColor
                )
                Spacer(Modifier.width(10.dp))
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = hint,
                            color = secondaryColor,
                            fontSize = 15.sp,
                            lineHeight = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(38.dp),
                        enabled = enabled
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_close),
                            contentDescription = stringResource(R.string.clear),
                            modifier = Modifier.size(20.dp),
                            tint = secondaryColor
                        )
                    }
                }
            }
        }
    )
}
