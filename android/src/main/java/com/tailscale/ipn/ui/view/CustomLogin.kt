// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.theme.listItem
import com.tailscale.ipn.ui.util.Lists
import com.tailscale.ipn.ui.util.ServerConfig
import com.tailscale.ipn.ui.util.set
import com.tailscale.ipn.ui.viewModel.LoginWithAuthKeyViewModel
import com.tailscale.ipn.ui.viewModel.LoginWithCustomControlURLViewModel

data class LoginViewStrings(
    var title: String,
    var explanation: String,
    var inputTitle: String,
    var placeholder: String,
)

@Composable
fun LoginWithCustomControlURLView(
    onNavigateHome: BackNavigation,
    backToSettings: BackNavigation,
    viewModel: LoginWithCustomControlURLViewModel = LoginWithCustomControlURLViewModel()
) {

  Scaffold(
      topBar = {
        Header(
            R.string.add_account,
            onBack = backToSettings,
        )
      }) { innerPadding ->
        val error by viewModel.errorDialog.collectAsState()
        val user by viewModel.loggedInUser.collectAsState()
        val customAdminUrl by ServerConfig.customAdminUrl.collectAsState()

        var controlUrlText by remember { mutableStateOf(user?.ControlURL ?: "") }
        var adminUrlText by remember { mutableStateOf(customAdminUrl ?: "") }

        error?.let { ErrorDialog(type = it, action = { viewModel.errorDialog.set(null) }) }

        Column(
            modifier =
                Modifier.padding(innerPadding)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .background(MaterialTheme.colorScheme.surface)) {
              // Section 1: Custom Control Server
              ListItem(
                  colors = MaterialTheme.colorScheme.listItem,
                  headlineContent = { Text(text = stringResource(id = R.string.custom_control_menu)) },
                  supportingContent = {
                    Text(text = stringResource(id = R.string.custom_control_menu_desc))
                  })

              ListItem(
                  colors = MaterialTheme.colorScheme.listItem,
                  headlineContent = {
                    Text(text = stringResource(id = R.string.custom_control_url_title))
                  },
                  supportingContent = {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        value = controlUrlText,
                        onValueChange = { controlUrlText = it },
                        placeholder = {
                          Text(
                              stringResource(id = R.string.custom_control_placeholder),
                              style = MaterialTheme.typography.bodySmall)
                        },
                        keyboardOptions =
                            KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                imeAction = ImeAction.Go),
                        keyboardActions =
                            KeyboardActions(onGo = { viewModel.setControlURL(controlUrlText, onNavigateHome) }))
                  })

              ListItem(
                  colors = MaterialTheme.colorScheme.listItem,
                  headlineContent = {
                    Box(modifier = Modifier.fillMaxWidth()) {
                      Button(
                          onClick = { viewModel.setControlURL(controlUrlText, onNavigateHome) },
                          content = { Text(stringResource(id = R.string.add_account_short)) })
                    }
                  })

              Lists.SectionDivider()

              // Section 2: Admin Console URL
              ListItem(
                  colors = MaterialTheme.colorScheme.listItem,
                  headlineContent = {
                    Text(text = stringResource(id = R.string.custom_admin_console_url))
                  },
                  supportingContent = {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        value = adminUrlText,
                        onValueChange = { adminUrlText = it },
                        placeholder = {
                          Text(
                              stringResource(id = R.string.custom_admin_console_placeholder),
                              style = MaterialTheme.typography.bodySmall)
                        },
                        keyboardOptions =
                            KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                imeAction = ImeAction.Done),
                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                  ServerConfig.setCustomAdminUrl(adminUrlText)
                                  backToSettings()
                                }))
                  })

              ListItem(
                  colors = MaterialTheme.colorScheme.listItem,
                  headlineContent = {
                    Box(modifier = Modifier.fillMaxWidth()) {
                      Button(
                          onClick = {
                            ServerConfig.setCustomAdminUrl(adminUrlText)
                            backToSettings()
                          },
                          content = { Text(stringResource(id = R.string.save)) })
                    }
                  })
            }
      }
}

@Composable
fun LoginWithAuthKeyView(
    onNavigateHome: BackNavigation,
    backToSettings: BackNavigation,
    viewModel: LoginWithAuthKeyViewModel = LoginWithAuthKeyViewModel()
) {

  Scaffold(
      topBar = {
        Header(
            R.string.add_account,
            onBack = backToSettings,
        )
      }) { innerPadding ->
        val error by viewModel.errorDialog.collectAsState()
        val strings =
            LoginViewStrings(
                title = stringResource(id = R.string.auth_key_title),
                explanation = stringResource(id = R.string.auth_key_explanation),
                inputTitle = stringResource(id = R.string.auth_key_input_title),
                placeholder = stringResource(id = R.string.auth_key_placeholder),
            )

        error?.let { ErrorDialog(type = it, action = { viewModel.errorDialog.set(null) }) }

        LoginView(
            innerPadding = innerPadding,
            strings = strings,
            onSubmitAction = { viewModel.setAuthKey(it, onNavigateHome) })
      }
}

@Composable
fun LoginView(
    innerPadding: PaddingValues = PaddingValues(16.dp),
    strings: LoginViewStrings,
    onSubmitAction: (String) -> Unit,
) {

  var textVal by remember { mutableStateOf("") }

  Column(
      modifier =
          Modifier.padding(innerPadding)
              .fillMaxWidth()
              .background(MaterialTheme.colorScheme.surface)) {
        ListItem(
            colors = MaterialTheme.colorScheme.listItem,
            headlineContent = { Text(text = strings.title) },
            supportingContent = { Text(text = strings.explanation) })

        ListItem(
            colors = MaterialTheme.colorScheme.listItem,
            headlineContent = { Text(text = strings.inputTitle) },
            supportingContent = {
              OutlinedTextField(
                  modifier = Modifier.fillMaxWidth(),
                  colors =
                      TextFieldDefaults.colors(
                          focusedContainerColor = Color.Transparent,
                          unfocusedContainerColor = Color.Transparent),
                  textStyle = MaterialTheme.typography.bodyMedium,
                  value = textVal,
                  onValueChange = { textVal = it },
                  placeholder = {
                    Text(strings.placeholder, style = MaterialTheme.typography.bodySmall)
                  },
                  keyboardOptions =
                      KeyboardOptions(
                          capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Go),
                  keyboardActions = KeyboardActions(onGo = { onSubmitAction(textVal) }))
            })

        ListItem(
            colors = MaterialTheme.colorScheme.listItem,
            headlineContent = {
              Box(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onSubmitAction(textVal) },
                    content = { Text(stringResource(id = R.string.add_account_short)) })
              }
            })
      }
}
