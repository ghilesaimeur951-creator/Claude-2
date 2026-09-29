package com.streetblocks.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streetblocks.app.audio.AudioCues
import com.streetblocks.app.data.model.AppSettings
import com.streetblocks.app.data.model.kgLabel
import com.streetblocks.app.ui.LocalAppContainer
import com.streetblocks.app.ui.SectionTitle
import com.streetblocks.app.ui.Stepper
import com.streetblocks.app.ui.SwitchRow
import com.streetblocks.app.ui.theme.Lime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val c = LocalAppContainer.current
    val s by c.settings.settings.collectAsStateWithLifecycle()

    fun update(f: (AppSettings) -> AppSettings) {
        c.settings.update(f)
        c.audio.configure(c.settings.settings.value)
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("RÉGLAGES", style = MaterialTheme.typography.headlineMedium, color = Lime)

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            colors = CardDefaults.cardColors(containerColor = if (s.outdoorMode) Lime else MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val fg = if (s.outdoorMode) Color.Black else Color.White
                    Text("MODE ENTRAÎNEMENT EXTÉRIEUR", fontWeight = FontWeight.Black, color = fg)
                    Text(
                        "Volume alarme au maximum, sons doublés et saturés pour porter loin, vibrations renforcées, voix plus posée.",
                        color = fg.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = s.outdoorMode, onCheckedChange = { v -> update { it.copy(outdoorMode = v) } })
            }
        }

        SectionTitle("Signaux")
        SwitchRow("Sons", s.sounds, { v -> update { it.copy(sounds = v) } }, "Bips puissants sur le flux alarme (audibles même volume média bas)")
        SwitchRow("Annonces vocales", s.voice, { v -> update { it.copy(voice = v) } }, "Exercice, série, répétitions, charge, repos")
        SwitchRow("Vibrations", s.vibration, { v -> update { it.copy(vibration = v) } })
        SwitchRow("Boost de volume", s.volumeBoost, { v -> update { it.copy(volumeBoost = v) } }, "Amplification logicielle des bips")
        SwitchRow("Alertes plein écran", s.fullScreenAlerts, { v -> update { it.copy(fullScreenAlerts = v) } }, "Si l'appli est en arrière-plan, rouvre l'écran de séance à chaque étape")

        SectionTitle("Minuteur")
        Stepper("Bips du compte à rebours", "${s.countdownSec} s", { update { it.copy(countdownSec = (it.countdownSec - 1).coerceAtLeast(0)) } }, { update { it.copy(countdownSec = (it.countdownSec + 1).coerceAtMost(10)) } }, sub = "Dernières secondes de chaque étape")
        Stepper("Préparation avant exercice", "${s.prepSec} s", { update { it.copy(prepSec = (it.prepSec - 5).coerceAtLeast(0)) } }, { update { it.copy(prepSec = (it.prepSec + 5).coerceAtMost(60)) } }, sub = "Temps pour se placer (sauf après un repos)")
        Stepper("Durée par répétition", "${s.secPerRep} s", { update { it.copy(secPerRep = (it.secPerRep - 1).coerceAtLeast(1)) } }, { update { it.copy(secPerRep = (it.secPerRep + 1).coerceAtMost(10)) } }, sub = "Sert à estimer la durée d'une série en répétitions")

        SectionTitle("Progression")
        Stepper("Incrément de charge", kgLabel(s.loadIncrementKg), { update { it.copy(loadIncrementKg = (it.loadIncrementKg - 0.5).coerceAtLeast(0.5)) } }, { update { it.copy(loadIncrementKg = (it.loadIncrementKg + 0.5).coerceAtMost(10.0)) } })

        SectionTitle("Tester les sons")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "Début exercice" to AudioCues.Cue.START,
                "Fin de série" to AudioCues.Cue.SET_END,
                "Fin de repos" to AudioCues.Cue.GO,
                "Changement" to AudioCues.Cue.CHANGE,
                "Décompte" to AudioCues.Cue.TICK,
                "Fin de séance" to AudioCues.Cue.SESSION_END,
            ).forEach { (label, cue) ->
                AssistChip(onClick = {
                    c.audio.configure(s)
                    c.audio.play(cue)
                }, label = { Text(label) })
            }
            AssistChip(onClick = {
                c.audio.configure(s)
                c.audio.speak("Tractions lestées. Série 2 sur 5. 10 répétitions, plus 15 kilos. C'est parti !")
            }, label = { Text("Voix") })
        }
        Text(
            "Astuce : pendant la séance, l'écran reste allumé. Si tu verrouilles le téléphone, le minuteur, les sons et la voix continuent.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text("Street Blocks 1.0 · 100 % hors ligne", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
}
