package com.tracksnatcher.app.ui.paywall

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.tracksnatcher.app.billing.BillingPeriod
import com.tracksnatcher.app.billing.PurchaseResult
import com.tracksnatcher.app.billing.SubscriptionProduct

private val PRO_BENEFITS = listOf(
    "Unlimited snatches — no monthly cap",
    "Up to 4 one-tap playlist buttons",
    "Smart Vibe Match auto-filing",
    "Android Auto & voice add",
    "Sonic Memory location cards to share",
)

@Composable
fun PaywallScreen(
    onDismiss: () -> Unit,
    onPurchased: () -> Unit,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PurchaseResult.Purchased, is PurchaseResult.Restored -> onPurchased()
                else -> Unit // Cancelled / Pending / Error / NothingToRestore: stay on paywall
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Text(
                text = "TrackSnatcher Pro",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Snatch every song. File it anywhere. Instantly.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
            PRO_BENEFITS.forEach { benefit ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(benefit, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(28.dp))
            if (products.isEmpty()) {
                Text(
                    "Loading plans…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                products.forEach { product ->
                    PlanCard(
                        product = product,
                        onSelect = { activity?.let { viewModel.purchase(it, product.productId) } },
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = viewModel::restore,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Restore Purchases")
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "Subscriptions renew automatically until cancelled. Lifetime is a one-time " +
                    "purchase. Manage or cancel anytime in Google Play.",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PlanCard(product: SubscriptionProduct, onSelect: () -> Unit) {
    val highlighted = product.isBestValue
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = if (highlighted) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(product.period.label(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    product.period.subtitle()?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(product.formattedPrice, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSelect,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(if (product.period == BillingPeriod.LIFETIME) "Unlock Lifetime" else "Subscribe")
            }
        }
    }
}

private fun BillingPeriod.label(): String = when (this) {
    BillingPeriod.MONTHLY -> "Monthly"
    BillingPeriod.YEARLY -> "Yearly"
    BillingPeriod.LIFETIME -> "Lifetime"
}

private fun BillingPeriod.subtitle(): String? = when (this) {
    BillingPeriod.YEARLY -> "Best value — save vs monthly"
    BillingPeriod.LIFETIME -> "Pay once, own forever"
    BillingPeriod.MONTHLY -> null
}
