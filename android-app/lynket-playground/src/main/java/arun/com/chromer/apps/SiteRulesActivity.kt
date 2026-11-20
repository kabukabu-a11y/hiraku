/*
 *
 *  Lynket
 *
 *  Copyright (C) 2023 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package arun.com.chromer.apps

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import arun.com.chromer.R
import arun.com.chromer.customtab.LinkPreferences
import arun.com.chromer.customtab.OpenMethod
import arun.com.chromer.customtab.displayLabel
import arun.com.chromer.theme.LynketTheme
import arun.com.chromer.ui.HirakuBottomBar
import arun.com.chromer.ui.openHomeTab

/**
 * Per-site (domain) open rules (v2.2.0). A rule pins how links to a
 * domain open; per-app rules still win over these.
 */
class SiteRulesActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      LynketTheme {
        SiteRulesScreen(onBack = { finish() })
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SiteRulesScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.site_rules_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.act_back))
          }
        }
      )
    },
    bottomBar = {
      HirakuBottomBar(
        selectedIndex = 1,
        onSelect = { index ->
          if (index != 1) {
            openHomeTab(context, index)
            (context as? Activity)?.finish()
          }
        }
      )
    }
  ) { padding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
    ) {
      SiteRulesBody()
    }
  }
}

/**
 * The site rules list with add/edit, shared between the standalone
 * screen and the main screen's Sites tab (v2.3.0).
 */
@Composable
fun SiteRulesBody() {
  val context = LocalContext.current
  var rules by remember { mutableStateOf(LinkPreferences.getSiteRules(context)) }
  var editingHost by remember { mutableStateOf<String?>(null) }
  var showAdd by remember { mutableStateOf(false) }

  fun refresh() {
    rules = LinkPreferences.getSiteRules(context)
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
  ) {
    Text(
      text = stringResource(R.string.site_rules_desc),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(12.dp))
    Button(
      onClick = { showAdd = true },
      modifier = Modifier.fillMaxWidth()
    ) {
      Text(stringResource(R.string.site_rules_add))
    }
    Spacer(modifier = Modifier.height(12.dp))
    if (rules.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(bottom = 96.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = stringResource(R.string.site_rules_empty),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
      ) {
        items(rules.entries.toList(), key = { it.key }) { (host, method) ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { editingHost = host }
              .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(text = host)
              Text(
                text = method.displayLabel(context),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }
    }
  }

  if (showAdd) {
    var domainText by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showAdd = false },
      title = { Text(stringResource(R.string.site_rules_add)) },
      text = {
        OutlinedTextField(
          value = domainText,
          onValueChange = { domainText = it },
          label = { Text(stringResource(R.string.site_rules_domain_hint)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            val host = LinkPreferences.normalizeHost(domainText)
            if (host != null) {
              showAdd = false
              editingHost = host
            }
          }
        ) {
          Text(stringResource(R.string.act_add))
        }
      },
      dismissButton = {
        TextButton(onClick = { showAdd = false }) {
          Text(stringResource(R.string.method_cancel))
        }
      }
    )
  }

  editingHost?.let { host ->
    var selected by remember(host) {
      mutableStateOf(LinkPreferences.getSiteRules(context)[host])
    }
    AlertDialog(
      onDismissRequest = { editingHost = null },
      title = { Text(host) },
      text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                LinkPreferences.setSiteRule(context, host, null)
                refresh()
                editingHost = null
              },
            verticalAlignment = Alignment.CenterVertically
          ) {
            RadioButton(selected = selected == null, onClick = {
              LinkPreferences.setSiteRule(context, host, null)
              refresh()
              editingHost = null
            })
            Text(stringResource(R.string.site_rules_default_option))
          }
          for (method in OpenMethod.CHOICES) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  LinkPreferences.setSiteRule(context, host, method)
                  refresh()
                  editingHost = null
                },
              verticalAlignment = Alignment.CenterVertically
            ) {
              RadioButton(selected = selected == method, onClick = {
                LinkPreferences.setSiteRule(context, host, method)
                refresh()
                editingHost = null
              })
              Text(method.displayLabel(context))
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { editingHost = null }) {
          Text(stringResource(R.string.act_close))
        }
      }
    )
  }
}
