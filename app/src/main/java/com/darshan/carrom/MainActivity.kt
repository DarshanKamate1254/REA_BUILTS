package com.darshan.carrom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CarromGame()
            }
        }
    }
}

private data class Piece(
    val x: Float,
    val y: Float,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val radius: Float = 0.024f,
    val color: Color,
    val points: Int = 1,
    val striker: Boolean = false,
    val pocketed: Boolean = false,
)

private data class PhysicsResult(
    val pieces: List<Piece>,
    val pointsEarned: Int,
    val strikerFoul: Boolean,
    val settled: Boolean,
)

private val BoardWood = Color(0xFFB96835)
private val BoardSurface = Color(0xFFEAC894)
private val DarkInk = Color(0xFF33241E)
private val Cream = Color(0xFFFFF8EE)
private val Teal = Color(0xFF2D8477)

@Composable
private fun CarromGame() {
    var pieces by remember { mutableStateOf(initialPieces()) }
    var scores by remember { mutableStateOf(listOf(0, 0)) }
    var currentPlayer by remember { mutableIntStateOf(0) }
    var moving by remember { mutableStateOf(false) }
    var aiming by remember { mutableStateOf(false) }
    var aimOffset by remember { mutableStateOf(Offset.Zero) }
    var shotPlayer by remember { mutableIntStateOf(0) }
    var pocketedThisTurn by remember { mutableStateOf(false) }
    var foulThisTurn by remember { mutableStateOf(false) }
    val gameOver = scores.any { it >= 10 }

    LaunchedEffect(moving) {
        if (!moving) return@LaunchedEffect
        var previousFrame = 0L
        while (moving) {
            val now = withFrameNanos { it }
            val delta = if (previousFrame == 0L) 1f / 60f
            else ((now - previousFrame) / 1_000_000_000f).coerceIn(0f, 0.04f)
            previousFrame = now

            val result = advancePieces(pieces, delta)
            pieces = result.pieces
            if (result.pointsEarned > 0) {
                scores = scores.toMutableList().also {
                    it[shotPlayer] += result.pointsEarned
                }
                pocketedThisTurn = true
            }
            if (result.strikerFoul) foulThisTurn = true
            if (result.settled) {
                moving = false
                if (!pocketedThisTurn || foulThisTurn) currentPlayer = 1 - shotPlayer
                pocketedThisTurn = false
                foulThisTurn = false
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF182521),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "DARSHAN CARROM",
                color = Cream,
                fontSize = 23.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PlayerCard(
                    name = "PLAYER 1",
                    score = scores[0],
                    active = currentPlayer == 0 && !moving,
                    modifier = Modifier.weight(1f),
                )
                PlayerCard(
                    name = "PLAYER 2",
                    score = scores[1],
                    active = currentPlayer == 1 && !moving,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = when {
                    gameOver -> "Player ${scores.indexOf(scores.max()) + 1} wins!"
                    moving -> "Pieces in motion"
                    else -> "Drag the striker back, then release"
                },
                color = Color(0xFFC9D2CC),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(14.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { position ->
                                val side = min(size.width, size.height).toFloat()
                                val center = Offset(side * 0.5f, side * 0.79f)
                                val strikerRadius = side * 0.033f
                                if (!moving && scores.none { it >= 10 } && hypot(
                                        position.x - center.x,
                                        position.y - center.y,
                                    ) <= strikerRadius * 2.2f
                                ) {
                                    aiming = true
                                    aimOffset = Offset.Zero
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (aiming && !moving && scores.none { it >= 10 }) {
                                    change.consume()
                                    aimOffset += dragAmount
                                }
                            },
                            onDragEnd = {
                                if (aiming && !moving) {
                                    val side = min(size.width, size.height).toFloat()
                                    val pullX = (-aimOffset.x / side * 3.8f).coerceIn(-1.5f, 1.5f)
                                    val pullY = (-aimOffset.y / side * 3.8f).coerceIn(-1.5f, 1.5f)
                                    if (hypot(pullX, pullY) > 0.08f) {
                                        val strikerIndex = pieces.indexOfFirst { it.striker }
                                        if (strikerIndex >= 0) {
                                            pieces = pieces.toMutableList().also { list ->
                                                list[strikerIndex] = list[strikerIndex].copy(
                                                    vx = pullX,
                                                    vy = pullY,
                                                )
                                            }
                                            shotPlayer = currentPlayer
                                            pocketedThisTurn = false
                                            foulThisTurn = false
                                            moving = true
                                        }
                                    }
                                    aiming = false
                                    aimOffset = Offset.Zero
                                }
                            },
                            onDragCancel = {
                                aiming = false
                                aimOffset = Offset.Zero
                            },
                        )
                    },
            ) {
                val side = min(size.width, size.height)
                val offsetX = (size.width - side) / 2f
                val offsetY = (size.height - side) / 2f
                drawContext.transform.translate(offsetX, offsetY)

                drawRect(BoardWood, size = Size(side, side))
                val inset = side * 0.055f
                drawRect(
                    BoardSurface,
                    topLeft = Offset(inset, inset),
                    size = Size(side - inset * 2f, side - inset * 2f),
                )
                val left = side * 0.12f
                val right = side * 0.88f
                val top = side * 0.12f
                val bottom = side * 0.88f

                drawRoundRect(
                    color = BoardWood,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(side * 0.025f),
                    style = Stroke(width = side * 0.008f),
                )
                drawCircle(
                    color = BoardWood,
                    radius = side * 0.105f,
                    center = Offset(side * 0.5f, side * 0.5f),
                    style = Stroke(width = side * 0.006f),
                )
                drawCircle(
                    color = Color(0xFFB7473D),
                    radius = side * 0.035f,
                    center = Offset(side * 0.5f, side * 0.5f),
                )
                drawLine(
                    BoardWood,
                    Offset(left, side * 0.79f),
                    Offset(right, side * 0.79f),
                    strokeWidth = side * 0.004f,
                )
                drawLine(
                    BoardWood,
                    Offset(left, side * 0.21f),
                    Offset(right, side * 0.21f),
                    strokeWidth = side * 0.004f,
                )

                val pocketRadius = side * 0.055f
                listOf(
                    Offset(side * 0.105f, side * 0.105f),
                    Offset(side * 0.895f, side * 0.105f),
                    Offset(side * 0.105f, side * 0.895f),
                    Offset(side * 0.895f, side * 0.895f),
                ).forEach { pocket ->
                    drawCircle(DarkInk, pocketRadius, pocket)
                    drawCircle(Color(0xFF66412B), pocketRadius * 0.72f, pocket)
                }

                if (aiming && hypot(aimOffset.x, aimOffset.y) > side * 0.025f) {
                    val strikerCenter = Offset(side * 0.5f, side * 0.79f)
                    val direction = Offset(-aimOffset.x, -aimOffset.y)
                    val length = hypot(direction.x, direction.y).coerceAtMost(side * 0.28f)
                    val unit = direction / hypot(direction.x, direction.y)
                    drawLine(
                        Teal,
                        strikerCenter,
                        strikerCenter + unit * length,
                        strokeWidth = side * 0.008f,
                        cap = StrokeCap.Round,
                    )
                }

                pieces.filterNot { it.pocketed }.forEach { piece ->
                    val center = Offset(piece.x * side, piece.y * side)
                    val radius = side * piece.radius
                    drawCircle(Color(0x55000000L), radius * 1.12f, center + Offset(0f, radius * 0.18f))
                    drawCircle(piece.color, radius, center)
                    drawCircle(
                        if (piece.striker) Cream else Color(0x66FFFFFF),
                        radius * 0.68f,
                        center,
                        style = Stroke(width = side * 0.004f),
                    )
                }
                drawContext.transform.translate(-offsetX, -offsetY)
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Pocket coins to score · First to 10 wins",
                    color = Color(0xFFC9D2CC),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        pieces = initialPieces()
                        scores = listOf(0, 0)
                        currentPlayer = 0
                        moving = false
                        aiming = false
                        aimOffset = Offset.Zero
                        pocketedThisTurn = false
                        foulThisTurn = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Teal,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("New game", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Local pass-and-play · Original edition",
                color = Color(0xFF84938B),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun PlayerCard(
    name: String,
    score: Int,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = if (active) Color(0xFF28423A) else Color(0xFF22312C),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    name,
                    color = if (active) Color(0xFF8FD2B3) else Color(0xFFA8B5AE),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Text(
                    if (score >= 10) "Winner" else if (active) "Your turn" else "Waiting",
                    color = Cream,
                    fontSize = 12.sp,
                )
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(if (active) Teal else Color(0xFF42534B), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    score.toString(),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun initialPieces(): List<Piece> {
    val black = Color(0xFF3B302A)
    val ivory = Color(0xFFFFF2D8)
    val pieces = mutableListOf(
        Piece(0.5f, 0.5f, radius = 0.027f, color = Color(0xFFB7473D), points = 3),
    )
    val ring = listOf(
        0.5f to 0.435f,
        0.556f to 0.468f,
        0.556f to 0.532f,
        0.5f to 0.565f,
        0.444f to 0.532f,
        0.444f to 0.468f,
    )
    ring.forEachIndexed { index, (x, y) ->
        pieces += Piece(x, y, color = if (index % 2 == 0) ivory else black)
    }
    val outerRing = listOf(
        0.5f to 0.375f,
        0.61f to 0.437f,
        0.61f to 0.563f,
        0.5f to 0.625f,
        0.39f to 0.563f,
        0.39f to 0.437f,
    )
    outerRing.forEachIndexed { index, (x, y) ->
        pieces += Piece(x, y, color = if (index % 2 == 0) black else ivory)
    }
    pieces += Piece(
        0.5f,
        0.79f,
        radius = 0.033f,
        color = Teal,
        striker = true,
        points = 0,
    )
    return pieces
}

private fun advancePieces(pieces: List<Piece>, deltaSeconds: Float): PhysicsResult {
    val next = pieces.toMutableList()
    var earned = 0
    var strikerFoul = false
    val damping = kotlin.math.exp(-1.7f * deltaSeconds)

    for (index in next.indices) {
        val piece = next[index]
        if (piece.pocketed) continue

        var x = piece.x + piece.vx * deltaSeconds
        var y = piece.y + piece.vy * deltaSeconds
        var vx = piece.vx * damping
        var vy = piece.vy * damping

        val pocketCenters = listOf(
            0.105f to 0.105f,
            0.895f to 0.105f,
            0.105f to 0.895f,
            0.895f to 0.895f,
        )
        val inPocket = pocketCenters.any { (px, py) -> hypot(x - px, y - py) < 0.071f }
        if (inPocket) {
            if (piece.striker) {
                strikerFoul = true
                x = 0.5f
                y = 0.79f
                vx = 0f
                vy = 0f
            } else {
                earned += piece.points
                next[index] = piece.copy(pocketed = true, vx = 0f, vy = 0f)
                continue
            }
        }

        val edge = 0.115f + piece.radius
        val farEdge = 0.885f - piece.radius
        if (x < edge) {
            x = edge
            vx = kotlin.math.abs(vx) * 0.78f
        } else if (x > farEdge) {
            x = farEdge
            vx = -kotlin.math.abs(vx) * 0.78f
        }
        if (y < edge) {
            y = edge
            vy = kotlin.math.abs(vy) * 0.78f
        } else if (y > farEdge) {
            y = farEdge
            vy = -kotlin.math.abs(vy) * 0.78f
        }
        if (vx * vx + vy * vy < 0.0004f) {
            vx = 0f
            vy = 0f
        }
        next[index] = piece.copy(x = x, y = y, vx = vx, vy = vy)
    }

    for (first in next.indices) {
        var a = next[first]
        if (a.pocketed) continue
        for (second in first + 1 until next.size) {
            var b = next[second]
            if (b.pocketed) continue

            val dx = b.x - a.x
            val dy = b.y - a.y
            val distance = hypot(dx, dy)
            val minimumDistance = a.radius + b.radius
            if (distance <= 0f || distance >= minimumDistance) continue

            val nx = dx / distance
            val ny = dy / distance
            val overlap = (minimumDistance - distance) * 0.5f
            val relativeVelocity = (a.vx - b.vx) * nx + (a.vy - b.vy) * ny
            val impulse = if (relativeVelocity > 0f) relativeVelocity * 0.92f else 0f
            a = a.copy(
                x = a.x - nx * overlap,
                y = a.y - ny * overlap,
                vx = a.vx - impulse * nx,
                vy = a.vy - impulse * ny,
            )
            b = b.copy(
                x = b.x + nx * overlap,
                y = b.y + ny * overlap,
                vx = b.vx + impulse * nx,
                vy = b.vy + impulse * ny,
            )
            next[first] = a
            next[second] = b
        }
    }

    val settled = next.none { !it.pocketed && it.vx * it.vx + it.vy * it.vy >= 0.0004f }
    return PhysicsResult(next, earned, strikerFoul, settled)
}
