package com.junkfood.seal.ui.page.settings.general

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.TextButton
import com.junkfood.seal.util.ToastUtil
import com.junkfood.seal.util.YT_DLP_STABLE_DEFAULT
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import com.junkfood.seal.R
import com.junkfood.seal.ui.common.booleanState
import com.junkfood.seal.ui.common.intState
import com.junkfood.seal.ui.component.ConfirmButton
import com.junkfood.seal.ui.component.DismissButton
import com.junkfood.seal.ui.component.SealDialog
import com.junkfood.seal.util.PreferenceStrings.getUpdateIntervalText
import com.junkfood.seal.util.PreferenceUtil.getLong
import com.junkfood.seal.util.PreferenceUtil.updateBoolean
import com.junkfood.seal.util.PreferenceUtil.updateInt
import com.junkfood.seal.util.PreferenceUtil.updateLong
import com.junkfood.seal.util.UpdateIntervalList
import com.junkfood.seal.util.YT_DLP_AUTO_UPDATE
import com.junkfood.seal.util.YT_DLP_NIGHTLY
import com.junkfood.seal.util.YT_DLP_STABLE
import com.junkfood.seal.util.YT_DLP_UPDATE_CHANNEL
import com.junkfood.seal.util.YT_DLP_UPDATE_INTERVAL
import com.junkfood.seal.util.YT_DLP_STABLE_URL
import com.junkfood.seal.util.PreferenceUtil.getString
import com.junkfood.seal.util.PreferenceUtil.updateString
import com.junkfood.seal.App
import com.junkfood.seal.util.YT_DLP_VERSION
import com.junkfood.seal.util.UpdateUtil
import kotlinx.coroutines.launch

@Composable
private fun DialogSingleChoiceItem(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    label: String,
    labelContainerColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .selectable(selected = selected, enabled = true, onClick = onClick)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        RadioButton(
            modifier = Modifier.clearAndSetSemantics {},
            selected = selected,
            onClick = onClick,
        )

        Text(text = text, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.weight(1f))
        Surface(modifier.padding(end = 12.dp), shape = CircleShape, color = labelContainerColor) {
            Text(
                modifier = Modifier.padding(4.dp),
                text = label,
                color = MaterialTheme.colorScheme.contentColorFor(labelContainerColor),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtdlpUpdateChannelDialog(modifier: Modifier = Modifier, onDismissRequest: () -> Unit) {
    var ytdlpUpdateChannel by YT_DLP_UPDATE_CHANNEL.intState
    var ytdlpAutoUpdate by YT_DLP_AUTO_UPDATE.booleanState
    var updateInterval by remember { mutableLongStateOf(YT_DLP_UPDATE_INTERVAL.getLong()) }
    var stableUrl by remember { mutableStateOf(YT_DLP_STABLE_URL.getString()) }
    var stableUrlError by remember { mutableStateOf(false) }
    var trustSource by remember { mutableStateOf(false) }

    fun isValidReleaseUrl(url: String): Boolean {
        val regex = Regex("^https://api\\.github\\.com/repos/[^/]+/[^/]+/releases/latest$")
        return regex.matches(url)
    }

    fun looksLikeRepoBranchOrRaw(url: String): Boolean {
        val repoBranch = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+@.+$")
        return repoBranch.matches(url) || url.startsWith("https://raw.githubusercontent.com/")
    }

    SealDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(id = R.string.update)) },
        icon = { Icon(Icons.Outlined.SyncAlt, null) },
        text = {
            LazyColumn() {
                item {
                    Text(
                        text = stringResource(id = R.string.update_channel),
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(top = 16.dp, bottom = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                item {
                    DialogSingleChoiceItem(
                        text = "yt-dlp",
                        selected = ytdlpUpdateChannel == YT_DLP_STABLE,
                        label = "Stable",
                    ) {
                        ytdlpUpdateChannel = YT_DLP_STABLE
                    }
                }
                item {
                    DialogSingleChoiceItem(
                        text = "yt-dlp-nightly-builds",
                        selected = ytdlpUpdateChannel == YT_DLP_NIGHTLY,
                        label = "Nightly",
                        labelContainerColor = MaterialTheme.colorScheme.tertiary,
                    ) {
                        ytdlpUpdateChannel = YT_DLP_NIGHTLY
                    }
                }
                item {
                    Text(
                        text = stringResource(id = R.string.additional_settings),
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(top = 16.dp, bottom = 16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                item {
                    var expanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                    ) {
                        OutlinedTextField(
                            value =
                                if (!ytdlpAutoUpdate) stringResource(id = R.string.disabled)
                                else getUpdateIntervalText(updateInterval),
                            onValueChange = {},
                            label = { Text(text = stringResource(id = R.string.auto_update)) },
                            readOnly = true,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            },
                        )
                        ExposedDropdownMenu(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.disabled)) },
                                onClick = {
                                    ytdlpAutoUpdate = false
                                    expanded = false
                                },
                            )
                            for ((interval, stringId) in UpdateIntervalList) {
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(id = stringId)) },
                                    onClick = {
                                        ytdlpAutoUpdate = true
                                        updateInterval = interval
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    if (ytdlpUpdateChannel == YT_DLP_STABLE) {
                        OutlinedTextField(
                            value = stableUrl,
                            onValueChange = {
                                stableUrl = it
                                stableUrlError = !isValidReleaseUrl(it)
                            },
                            label = { Text(text = stringResource(id = R.string.yt_dlp_stable_url)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            singleLine = true,
                            isError = stableUrlError,
                        )

                        if (stableUrlError) {
                            Text(
                                text = stringResource(id = R.string.invalid_release_url),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 24.dp, top = 6.dp)
                            )
                        }

                        // Show warning and trust checkbox if stableUrl is not a releases API URL
                        if (!isValidReleaseUrl(stableUrl)) {
                            Text(
                                text = stringResource(id = R.string.yt_dlp_custom_source_warning),
                                color = MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 24.dp, top = 6.dp)
                            )

                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Checkbox(
                                    checked = trustSource,
                                    onCheckedChange = { trustSource = it }
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(text = stringResource(id = R.string.yt_dlp_trust_checkbox))
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                stableUrl = YT_DLP_STABLE_DEFAULT
                                stableUrlError = false
                                trustSource = false
                            }) {
                                Text(text = stringResource(id = R.string.reset))
                            }
                        }

                        // Manual update button
                        var isUpdating by remember { mutableStateOf(false) }
                        val scope = rememberCoroutineScope()

                        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Spacer(modifier = Modifier.weight(1f))

                            if (isUpdating) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        isUpdating = true
                                        runCatching {
                                            UpdateUtil.updateYtDlp(ytdlpUpdateChannel, stableUrl)
                                        }
                                            .onFailure { th ->
                                                th.printStackTrace()
                                                App.context.makeToast(R.string.yt_dlp_update_fail)
                                            }
                                            .onSuccess {
                                                App.context.makeToast(App.context.getString(R.string.yt_dlp_up_to_date) + " (${YT_DLP_VERSION.getString()})")
                                            }
                                        isUpdating = false
                                    }
                                },
                                enabled = !isUpdating && (isValidReleaseUrl(stableUrl) || (looksLikeRepoBranchOrRaw(stableUrl) && trustSource)),
                            ) {
                                Text(text = stringResource(id = R.string.ytdlp_update))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            ConfirmButton {
                if (ytdlpUpdateChannel == YT_DLP_STABLE) {
                    if (!isValidReleaseUrl(stableUrl) && !looksLikeRepoBranchOrRaw(stableUrl)) {
                        ToastUtil.makeToast(R.string.invalid_release_url)
                        return@ConfirmButton
                    }
                    if (looksLikeRepoBranchOrRaw(stableUrl) && !trustSource) {
                        ToastUtil.makeToast(R.string.please_confirm_trust)
                        return@ConfirmButton
                    }
                }

                YT_DLP_AUTO_UPDATE.updateBoolean(ytdlpAutoUpdate)
                YT_DLP_UPDATE_CHANNEL.updateInt(ytdlpUpdateChannel)
                YT_DLP_UPDATE_INTERVAL.updateLong(updateInterval)
                YT_DLP_STABLE_URL.updateString(stableUrl)
                onDismissRequest()
            }
        },
        dismissButton = { DismissButton { onDismissRequest() } },
    )
}
