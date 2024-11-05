package com.example.lunchmate.ui.screens

import BottomNavBar
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.font.FontWeight

var nameofevent= ""
val etaOptions = (1..15).map { i -> String.format("%02d:%02d", i / 6, (i % 6) * 10) } // Generate times from 00:10 to 02:30
@Composable
fun EventsMade(navController: NavController, creatorName: String) {
    val eventsList = remember { mutableStateListOf<Event>() }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var selectedLocation by remember { mutableStateOf("Stockholm") } // Default selected location
    val eventManager = EventManager() // Instantiate EventManager

    // Debugging: Log when events are fetched
    LaunchedEffect(Unit) {
        try {
            val events = eventManager.fetchAndCleanEvents()
            println("Here is Events: $events")
            events.forEach { event ->
                if (!eventsList.contains(event)) {
                    eventsList.add(event)
                    println("Event fetched: ${event.eventName} at ${event.location}")
                }
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: "Unknown error"
            println("Error fetching events: $errorMessage")
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        eventManager.startListeningForEvents { newEvents ->
            newEvents.forEach { event ->
                if (!eventsList.contains(event)) {
                    eventsList.add(event)
                    println("New event detected: ${event.eventName} at ${event.location}")
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            BottomNavBar(navController)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Hello $creatorName!",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 20.sp),
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Where do you want to eat today?",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            var expanded by remember { mutableStateOf(false) }
            val locations = listOf("Stockholm", "Malmö", "Växjö", "Karlskrona", "Karlshamn", "Kalmar","Jönköping","Luleå","Uppsala","Sarajevo")

            Box(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Selected Location: $selectedLocation")
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    locations.forEach { loc ->
                        DropdownMenuItem(
                            text = { Text(loc) },
                            onClick = {
                                selectedLocation = loc
                                chaneloc = selectedLocation
                                expanded = false
                                println("Selected location: $selectedLocation")
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                isLoading -> CircularProgressIndicator()
                errorMessage.isNotEmpty() -> Text(errorMessage, color = Color.Red)
                eventsList.none { it.location == selectedLocation } -> Text("No events available for $selectedLocation")
                else -> {
                    LazyColumn {
                        items(eventsList.filter { it.location == selectedLocation }) { event ->
                            if (event.createdBy == creatorName) {
                                println("IM THE CREATOR NOW")
                                EventCreatorItem(event, navController)
                            } else {
                                println("IM THE USER NOW")
                                EventItem(event, navController)
                            }
                            println("Displaying event: ${event.eventName} at ${event.location}")
                        }
                    }
                }
            }
        }
    }
}






@Composable
fun EventItem(event: Event, navController: NavController) {
    var remainingTime by remember { mutableStateOf("Loading...") }
    val eventDateTime = "${event.eventDate} ${event.eventTime}"
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    var etaLeft by remember { mutableStateOf("Loading...") }

    // State to track if the event has ended
    val isEventEnded by remember { mutableStateOf(event.isEventEnded) }
    val etaStart by remember { mutableStateOf(event.etaStart) }


    LaunchedEffect(eventDateTime) {
        while (!isEventEnded) {
            try {
                val eventTimeParsed = dateFormat.parse(eventDateTime) ?: Date()
                val currentTime = Date()
                val timeDiff = eventTimeParsed.time - currentTime.time

                if (timeDiff > 0) {
                    val hours = (timeDiff / (1000 * 60 * 60)).toInt()
                    val minutes = ((timeDiff / (1000 * 60)) % 60).toInt()
                    val seconds = ((timeDiff / 1000) % 60).toInt()
                    remainingTime = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                } else {
                    remainingTime = "Event ended"

                    break
                }
            } catch (e: Exception) {
                remainingTime = "Invalid date"
                break
            }
            delay(1000)
        }
    }
    if (etaStart) {
        LaunchedEffect(event) {
            try {
                val estimatedTimeString = event.estimatedArrivalTime
                val calendar = Calendar.getInstance()
                val today = calendar.time
                val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val estimatedTimeOnly = dateFormat.parse(estimatedTimeString) ?: return@LaunchedEffect
                // Set the time from estimatedTimeString onto today's date
                calendar.time = today
                calendar.set(Calendar.HOUR_OF_DAY, estimatedTimeOnly.hours)
                calendar.set(Calendar.MINUTE, estimatedTimeOnly.minutes)
                calendar.set(Calendar.SECOND, 0)
                val estimatedTime = calendar.time
                println("Estimated Time: $estimatedTime" )

                while (etaStart) {
                    val currentTime = Date()
                    val timeDiff = estimatedTime.time - currentTime.time
                    println("timeDiff: $timeDiff" )
                    if (timeDiff > 0) {
                        val hours = (timeDiff / (1000 * 60 * 60)).toInt()
                        val minutes = ((timeDiff / (1000 * 60)) % 60).toInt()
                        val seconds = ((timeDiff / 1000) % 60).toInt()
                        etaLeft = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                    } else {
                        etaLeft = "Arrived"
                        break
                    }

                    delay(1000)
                }
            } catch (e: Exception) {
                Log.e("EventCreatorItem", "Error in ETA countdown: ${e.localizedMessage}")
            }
        }
    }

    // Determine the label for remaining time or ETA display
    val timeDisplayLabel = if (etaStart) "Estimated Time of Arrival" else "Time Left"
    val timeDisplay = if (etaStart) etaLeft else remainingTime


    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.eventName,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 22.sp),
                    color = Color.Black,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            nameofevent = event.eventName
                            navController.navigate("event_details")
                        }
                )
                Button(
                    onClick = {
                        if (isEventEnded) {
                            navController.navigate("review_screen")
                        } else {
                            // Handle Menu action
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White)
                ) {
                    Text(if (isEventEnded) "Rate your Food" else "Menu")
                }
            }

            Text(
                text = event.eventDescription,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.Black,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Filled.CalendarToday, contentDescription = "Event Date")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Date: ${event.eventDate}", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Filled.Person, contentDescription = "Created By")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Created by: ${event.createdBy}", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp))
            }
            Text(event.pickupDineIn, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp))
            Text("People: ${event.peopleCount}", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp))

            // Display the correct label based on ETA or event time countdown
            Text("$timeDisplayLabel: $timeDisplay", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        navController.navigate("event_page")
                        // Handle place order logic
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        contentColor = Color.White
                    ),
                    enabled = !isEventEnded
                ) {
                    Icon(imageVector = Icons.Filled.PersonAdd, contentDescription = "Join Event")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Place Order")
                }
            }
        }
    }
}




@Composable
fun EventCreatorItem(event: Event, navController: NavController, onDeleteConfirmed: () -> Unit = {}) {
    var remainingTime by remember { mutableStateOf("Loading...") }
    var remainingTimeEta by remember { mutableStateOf("Loading...") }
    var isEditing by remember { mutableStateOf(false) }
    var eventDate by remember { mutableStateOf(event.eventDate) }
    var eventTime by remember { mutableStateOf(event.eventTime) }
    var isPickup by remember { mutableStateOf(event.pickupDineIn == "Pickup") }
    var pickupDineInOption by remember { mutableStateOf(event.pickupDineIn) }
    var showEtaInput by remember { mutableStateOf(false) }
    var estimatedArrivalTime by remember { mutableStateOf(event.estimatedArrivalTime) }
    var startEtaCountdown by remember { mutableStateOf(false) }
    var etaCompleted by remember { mutableStateOf(false) }
    var isEventEnded by remember { mutableStateOf(event.isEventEnded) }
    var etaStart by remember { mutableStateOf(event.etaStart) }

    val etaOptions = (1..15).map { i -> String.format("%02d:%02d", i / 6, (i % 6) * 10) }
    var selectedEta by remember { mutableStateOf(etaOptions[0]) }

    val eventDateTime = "$eventDate $eventTime"
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val eventManager = EventManager()

    // Timer logic to update remaining time and isEventEnded status
    LaunchedEffect(eventDateTime) {
        while (!event.isEventEnded or !isEventEnded) {
            try {
                val eventTimeParsed = dateFormat.parse(eventDateTime) ?: Date()
                val currentTime = Date()
                val timeDiff = eventTimeParsed.time - currentTime.time

                if (timeDiff > 0) {
                    val hours = (timeDiff / (1000 * 60 * 60)).toInt()
                    val minutes = ((timeDiff / (1000 * 60)) % 60).toInt()
                    val seconds = ((timeDiff / 1000) % 60).toInt()
                    remainingTime = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                    etaCompleted = false
                } else {
                    remainingTime = "Event ended"
                    etaCompleted = true
                    isEventEnded = true
                    eventManager.updateEvent(event.copy(isEventEnded = true)) { success ->
                        if (!success) {
                            Log.e("EventCreatorItem", "Failed to update event end status in database")
                        }
                    }
                    break
                }
            } catch (e: Exception) {
                remainingTime = "Invalid date"
                break
            }
            delay(1000)
        }
    }

    if (event.etaStart or etaStart) {
        LaunchedEffect(event) {
            try {
                val estimatedTimeString = event.estimatedArrivalTime
                val calendar = Calendar.getInstance()
                val today = calendar.time
                val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val estimatedTimeOnly = dateFormat.parse(estimatedTimeString) ?: return@LaunchedEffect
                // Set the time from estimatedTimeString onto today's date
                calendar.time = today
                calendar.set(Calendar.HOUR_OF_DAY, estimatedTimeOnly.hours)
                calendar.set(Calendar.MINUTE, estimatedTimeOnly.minutes)
                calendar.set(Calendar.SECOND, 0)
                val estimatedTime = calendar.time
                println("Estimated Time: $estimatedTime" )

                while (event.etaStart) {
                    val currentTime = Date()
                    val timeDiff = estimatedTime.time - currentTime.time
                    println("timeDiff: $timeDiff" )
                    if (timeDiff > 0) {
                        val hours = (timeDiff / (1000 * 60 * 60)).toInt()
                        val minutes = ((timeDiff / (1000 * 60)) % 60).toInt()
                        val seconds = ((timeDiff / 1000) % 60).toInt()
                        remainingTimeEta = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                    } else {
                        remainingTimeEta = "Arrived"
                        break
                    }

                    delay(1000)
                }
            } catch (e: Exception) {
                Log.e("EventCreatorItem", "Error in ETA countdown: ${e.localizedMessage}")
            }
        }
    }

        // Determine the label for remaining time or ETA display
    val timeDisplayLabel = if (etaStart) "Estimated Time of Arrival" else "Time Left"
    val timeDisplay = if (etaStart) remainingTimeEta else remainingTime

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)) // Light Blue
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.eventName,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 22.sp),
                    color = Color.Black,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            nameofevent = event.eventName
                            navController.navigate("event_details")
                        }
                )
            }
            Text(
                text = event.eventDescription,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.Black,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (isEditing) {
                TextField(
                    value = eventDate,
                    onValueChange = { eventDate = it },
                    label = { Text("Event Date") }
                )
                TextField(
                    value = eventTime,
                    onValueChange = { eventTime = it },
                    label = { Text("Event Time") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            isPickup = true
                            pickupDineInOption = "Pickup"
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPickup) Color(0xFFD1BCE3) else Color(0xFFFBE8E3),
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Pickup")
                    }
                    TextButton(
                        onClick = {
                            isPickup = false
                            pickupDineInOption = "Dine In"
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isPickup) Color(0xFFD1BCE3) else Color(0xFFFBE8E3),
                            contentColor = Color.Black
                        )
                    ) {
                        Text("Dine In")
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.CalendarToday, contentDescription = "Event Date")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Date: $eventDate", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.Person, contentDescription = "People Count")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("People: ${event.peopleCount}", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp))
                }
                Text("Option: ${if (pickupDineInOption == "Pickup") "Pickup" else "Dine In"}", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp))
            }

            // Display the correct label based on ETA or event time countdown
            Text("$timeDisplayLabel: $timeDisplay", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp))

            if (showEtaInput) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                ) {
                    TextButton(
                        onClick = { showEtaInput = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Estimated Time of Arrival: $selectedEta")
                    }

                    LazyColumn(modifier = Modifier.height(150.dp)) {
                        items(etaOptions) { eta ->
                            DropdownMenuItem(
                                text = { Text(eta) },
                                onClick = {
                                    selectedEta = eta
                                    showEtaInput = false

                                    // Extract ETA minutes from the selected option
                                    val etaMinutes = eta.filter { it.isDigit() }.toIntOrNull() ?: 0

                                    // Calculate arrival time based on the current time plus ETA duration
                                    val calendar = Calendar.getInstance()
                                    calendar.add(Calendar.MINUTE, etaMinutes)
                                    val calculatedArrivalTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.time)

                                    // Update the event with both etaStart and the calculated arrival time
                                    val updatedEvent = event.copy(
                                        etaStart = true,
                                        estimatedArrivalTime = calculatedArrivalTime
                                    )
                                    eventManager.updateEvent(updatedEvent) { success ->
                                        if (!success) {
                                            Log.e("EventCreatorItem", "Failed to update ETA and etaStart in database")
                                        } else {
                                            println("VALUE OF ETASTART: $etaStart")
                                            etaStart = event.etaStart // Update the local state if needed
                                            println("Selected ETA: $calculatedArrivalTime")
                                            // Additional actions like starting a countdown can go here
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }


// Set ETA Button Logic
            Button(
                onClick = { showEtaInput = !showEtaInput },
                enabled = etaCompleted || remainingTime == "Food has Arrived"
            ) {
                Text("Set ETA")
            }


            // Event Management Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (isEditing) {
                    Button(onClick = {
                        val updatedEvent = event.copy(
                            eventDate = eventDate,
                            eventTime = eventTime,
                            pickupDineIn = pickupDineInOption
                        )
                        eventManager.updateEvent(updatedEvent) { success ->
                            if (success) {
                                isEditing = false
                            } else {
                                Log.e("EventCreatorItem", "Failed to update event in database")
                            }
                        }
                    }) {
                        Text("Save")
                    }
                    Button(onClick = { isEditing = false }) {
                        Text("Cancel")
                    }
                } else {
                    Button(onClick = { isEditing = true }) {
                        Text("Edit")
                    }
                    Button(onClick = onDeleteConfirmed) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}








data class Event(
    val id: String = "",
    val eventName: String = "",
    val eventDate: String = "",
    val eventTime: String = "",
    val location: String = "",
    val pickupDineIn: String = "",
    val createdBy: String = "",
    val peopleCount: Int = 0,
    val eventDescription: String = "",
    val estimatedArrivalTime: String = "", // Add this line
    val isEventEnded: Boolean = false ,
    val etaStart: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Event) return false

        return eventName == other.eventName &&
                eventDate == other.eventDate &&
                eventTime == other.eventTime &&
                createdBy == other.createdBy
    }

    override fun hashCode(): Int {
        return Objects.hash(eventName, eventDate, eventTime, createdBy)
    }
}
