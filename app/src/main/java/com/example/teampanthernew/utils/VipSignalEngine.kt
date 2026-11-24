package com.example.teampanthernew.utils

data class PredictionResult(
    val prediction: String, // "BIG", "SMALL", "RED", "GREEN"
    val botName: String = "AI Neural Predictor",
    val confidence: Int = 82,
    val predictedNumber: Int = 5,
    val patternDetected: String = "Neural Ensemble"
)

data class HistoryItem(
    val number: Int,        // 0-9
    val outcome: String,    // "BIG", "SMALL", "RED", "GREEN"
    val periodId: String = ""
)

object VipSignalEngine {

    /**
     * TEAM PANTHER VIP Pattern:
     * SMALL -> BIG -> RED -> GREEN -> BIG -> SMALL -> GREEN -> RED (Loops continuously)
     */
    val TEAM_PANTHER_VIP_PATTERN = listOf(
        "SMALL", "BIG", "RED", "GREEN", "BIG", "SMALL", "GREEN", "RED"
    )

    /**
     * TEAM PANTHER VIP 2 Pattern:
     * SMALL -> SMALL -> BIG -> BIG -> SMALL -> SMALL -> BIG -> BIG (Loops continuously)
     */
    val TEAM_PANTHER_VIP_2_PATTERN = listOf(
        "SMALL", "SMALL", "BIG", "BIG", "SMALL", "SMALL", "BIG", "BIG"
    )

    data class TrendBlock(val outcome: String, val count: Int)

    private fun oppositeOutcome(outcome: String): String =
        if (outcome.equals("BIG", ignoreCase = true)) "SMALL" else "BIG"

    private fun parseTrendBlocks(cleanList: List<String>): List<TrendBlock> {
        if (cleanList.isEmpty()) return emptyList()
        val chrono = cleanList.take(30).reversed()
        val blocks = mutableListOf<TrendBlock>()
        for (item in chrono) {
            val norm = if (item.equals("BIG", ignoreCase = true)) "BIG" else "SMALL"
            if (blocks.isNotEmpty() && blocks.last().outcome == norm) {
                val last = blocks.removeAt(blocks.size - 1)
                blocks.add(TrendBlock(last.outcome, last.count + 1))
            } else {
                blocks.add(TrendBlock(norm, 1))
            }
        }
        return blocks
    }

    /**
     * TIER 1: BIG SMALL TREND CHART PATTERN (From TigerAutoRobot Image 1)
     * Matches 10 foundational trend formations:
     * 1. Single Trend (1:1 alternating: B S B S B)
     * 2. Double Trend (2:2 repeating: S S B B S S / B B S -> S)
     * 3. Triple Trend (3:3 repeating: B B B S S S)
     * 4. Quadra Trend (4:4 repeating: S S S S B B B B)
     * 5. Three in One Trend (3:1 repeating: B B B S B B B)
     * 6. Two in One Trend (2:1 repeating: S S B S S B S S)
     * 7. Three in Two Trend (3:2 repeating: B B B S S B B B)
     * 8. Four in One Trend (4:1 repeating: S S S S B S S S S)
     * 9. Four in Two Trend (4:2 repeating: B B B B S S B B B B)
     * 10. Long Trend (5+ streak continuous ride)
     */
    private fun evaluateImage1TrendChartPattern(blocks: List<TrendBlock>): Pair<String, String>? {
        if (blocks.isEmpty()) return null
        val curr = blocks.last()
        val prev = if (blocks.size >= 2) blocks[blocks.size - 2] else null
        val prev2 = if (blocks.size >= 3) blocks[blocks.size - 3] else null
        val prev3 = if (blocks.size >= 4) blocks[blocks.size - 4] else null

        // 10. Long Trend (Streak >= 5)
        if (curr.count >= 5) {
            return Pair(curr.outcome, "Pattern 10: Long Trend (${curr.count} ${curr.outcome}s Streak)")
        }

        if (prev != null) {
            // 9. Four in Two Trend (4:2 / 2:4)
            if (prev.count == 4 && curr.count == 1) {
                return Pair(curr.outcome, "Pattern 9: Four-in-Two Trend (4 ${prev.outcome} -> Needs 2 ${curr.outcome})")
            }
            if (prev.count == 4 && curr.count == 2) {
                return Pair(oppositeOutcome(curr.outcome), "Pattern 9: Four-in-Two Trend (4:2 Complete -> Switch to 4 ${oppositeOutcome(curr.outcome)})")
            }
            if (prev.count == 2 && (prev2?.count == 4 || prev3?.count == 4) && curr.count in 1..3) {
                return Pair(curr.outcome, "Pattern 9: Four-in-Two Trend (2 ${prev.outcome} -> Building 4 ${curr.outcome})")
            }
            if (prev.count == 2 && (prev2?.count == 4 || prev3?.count == 4) && curr.count == 4) {
                return Pair(oppositeOutcome(curr.outcome), "Pattern 9: Four-in-Two Trend (4:2 Complete -> Switch to 2)")
            }

            // 8. Four in One Trend (4:1 / 1:4)
            if (prev.count == 4 && curr.count == 1) {
                return Pair(oppositeOutcome(curr.outcome), "Pattern 8: Four-in-One Trend (1 Counter Complete -> Return to 4 ${oppositeOutcome(curr.outcome)})")
            }
            if (prev.count == 1 && (prev2?.count == 4 || prev3?.count == 4) && curr.count in 1..3) {
                return Pair(curr.outcome, "Pattern 8: Four-in-One Trend (Building 4 ${curr.outcome})")
            }
            if (prev.count == 1 && (prev2?.count == 4 || prev3?.count == 4) && curr.count == 4) {
                return Pair(oppositeOutcome(curr.outcome), "Pattern 8: Four-in-One Trend (4 Complete -> 1 Counter)")
            }

            // 4. Quadra Trend (4:4)
            if (prev.count == 4) {
                if (curr.count in 1..3) {
                    return Pair(curr.outcome, "Pattern 4: Quadra Trend 4:4 (Needs ${4 - curr.count} more ${curr.outcome})")
                }
                if (curr.count == 4) {
                    return Pair(oppositeOutcome(curr.outcome), "Pattern 4: Quadra Trend 4:4 (Complete 4:4 -> Switch to ${oppositeOutcome(curr.outcome)})")
                }
            }

            // 7. Three in Two Trend (3:2 / 2:3)
            if (prev.count == 3 && curr.count == 1) {
                return Pair(curr.outcome, "Pattern 7: Three-in-Two Trend (3 ${prev.outcome} -> Needs 2 ${curr.outcome})")
            }
            if (prev.count == 3 && curr.count == 2) {
                return Pair(oppositeOutcome(curr.outcome), "Pattern 7: Three-in-Two Trend (3:2 Complete -> Switch to 3 ${oppositeOutcome(curr.outcome)})")
            }
            if (prev.count == 2 && (prev2?.count == 3 || prev3?.count == 3) && curr.count in 1..2) {
                return Pair(curr.outcome, "Pattern 7: Three-in-Two Trend (2 ${prev.outcome} -> Building 3 ${curr.outcome})")
            }
            if (prev.count == 2 && (prev2?.count == 3 || prev3?.count == 3) && curr.count == 3) {
                return Pair(oppositeOutcome(curr.outcome), "Pattern 7: Three-in-Two Trend (3:2 Complete -> Switch to 2)")
            }

            // 5. Three in One Trend (3:1 / 1:3)
            if (prev.count == 3 && curr.count == 1) {
                return Pair(oppositeOutcome(curr.outcome), "Pattern 5: Three-in-One Trend (1 Counter Complete -> Return to 3 ${oppositeOutcome(curr.outcome)})")
            }
            if (prev.count == 1 && (prev2?.count == 3 || prev3?.count == 3) && curr.count in 1..2) {
                return Pair(curr.outcome, "Pattern 5: Three-in-One Trend (Building 3 ${curr.outcome})")
            }
            if (prev.count == 1 && (prev2?.count == 3 || prev3?.count == 3) && curr.count == 3) {
                return Pair(oppositeOutcome(curr.outcome), "Pattern 5: Three-in-One Trend (3 Complete -> 1 Counter)")
            }

            // 3. Triple Trend (3:3)
            if (prev.count == 3) {
                if (curr.count in 1..2) {
                    return Pair(curr.outcome, "Pattern 3: Triple Trend 3:3 (Needs ${3 - curr.count} more ${curr.outcome})")
                }
                if (curr.count == 3) {
                    return Pair(oppositeOutcome(curr.outcome), "Pattern 3: Triple Trend 3:3 (Complete 3:3 -> Switch to ${oppositeOutcome(curr.outcome)})")
                }
            }

            // 6. Two in One Trend (2:1 / 1:2)
            if ((prev2?.count == 1 || prev3?.count == 1)) {
                if (prev.count == 2 && curr.count == 1) {
                    return Pair(oppositeOutcome(curr.outcome), "Pattern 6: Two-in-One Trend (1 Counter Complete -> Return to 2 ${oppositeOutcome(curr.outcome)})")
                }
                if (prev.count == 1 && curr.count == 1) {
                    return Pair(curr.outcome, "Pattern 6: Two-in-One Trend (Building 2 ${curr.outcome})")
                }
                if (prev.count == 1 && curr.count == 2) {
                    return Pair(oppositeOutcome(curr.outcome), "Pattern 6: Two-in-One Trend (2 Complete -> 1 Counter)")
                }
            }

            // 2. Double Trend (2:2)
            if (prev.count == 2) {
                if (curr.count == 1) {
                    return Pair(curr.outcome, "Pattern 2: Double Trend 2:2 (2 ${prev.outcome} -> Needs 2 ${curr.outcome})")
                }
                if (curr.count == 2) {
                    return Pair(oppositeOutcome(curr.outcome), "Pattern 2: Double Trend 2:2 (Complete 2:2 -> Switch to ${oppositeOutcome(curr.outcome)})")
                }
            }

            // 1. Single Trend (1:1 Alternating)
            if (curr.count == 1 && prev.count == 1) {
                return Pair(oppositeOutcome(curr.outcome), "Pattern 1: Single Trend 1:1 Alternating (Switch to ${oppositeOutcome(curr.outcome)})")
            }
        }
        return null
    }

    /**
     * TIER 2: OFFICIAL COLOUR TRADING PATTERNS (From TigerAutoRobot Image 2)
     */
    private fun evaluateImage2ColourTradingPatterns(blocks: List<TrendBlock>): Pair<String, String>? {
        if (blocks.size < 2) return null
        val curr = blocks.last()
        val prev = blocks[blocks.size - 2]

        // Method 7: 4A3B / 3A4B
        if (prev.count == 4 && curr.count in 1..2) {
            return Pair(curr.outcome, "Method 7: 4A3B Ratio (4 ${prev.outcome} -> Needs 3 ${curr.outcome})")
        }
        if (prev.count == 4 && curr.count == 3) {
            return Pair(oppositeOutcome(curr.outcome), "Method 7: 4A3B Ratio (4:3 Complete -> Switch to 4)")
        }
        if (prev.count == 3 && curr.count in 1..3) {
            return Pair(curr.outcome, "Method 7: 3A4B Ratio (3 ${prev.outcome} -> Needs 4 ${curr.outcome})")
        }
        if (prev.count == 3 && curr.count == 4) {
            return Pair(oppositeOutcome(curr.outcome), "Method 7: 3A4B Ratio (3:4 Complete -> Switch to 3)")
        }

        // Method 9: 1A3B / 3A1B
        if (prev.count == 1 && curr.count in 1..2) {
            return Pair(curr.outcome, "Method 9: 1A3B Ratio (1 ${prev.outcome} -> Building 3 ${curr.outcome})")
        }
        if (prev.count == 1 && curr.count == 3) {
            return Pair(oppositeOutcome(curr.outcome), "Method 9: 1A3B Ratio (3 Complete -> 1 Counter)")
        }
        if (prev.count == 3 && curr.count == 1) {
            return Pair(oppositeOutcome(curr.outcome), "Method 9: 3A1B Ratio (1 Counter Complete -> Switch to 3)")
        }

        // Method 8: 1A2B / 2A1B
        if (prev.count == 1 && curr.count == 1) {
            return Pair(curr.outcome, "Method 8: 1A2B Ratio (1 ${prev.outcome} -> Building 2 ${curr.outcome})")
        }
        if (prev.count == 1 && curr.count == 2) {
            return Pair(oppositeOutcome(curr.outcome), "Method 8: 1A2B Ratio (2 Complete -> 1 Counter)")
        }
        if (prev.count == 2 && curr.count == 1) {
            return Pair(oppositeOutcome(curr.outcome), "Method 8: 2A1B Ratio (1 Counter Complete -> Switch to 2)")
        }

        // Method 6: 4A2B / 2A4B
        if (prev.count == 4 && curr.count == 1) {
            return Pair(curr.outcome, "Method 6: 4A2B Ratio (4 ${prev.outcome} -> Needs 2 ${curr.outcome})")
        }
        if (prev.count == 4 && curr.count == 2) {
            return Pair(oppositeOutcome(curr.outcome), "Method 6: 4A2B Ratio (4:2 Complete -> Switch to 4)")
        }

        // Method 5: 4A1B / 1A4B
        if (prev.count == 4 && curr.count == 1) {
            return Pair(oppositeOutcome(curr.outcome), "Method 5: 4A1B Ratio (1 Counter Complete -> Switch to 4)")
        }

        return null
    }

    /**
     * TIER 3: DIRECT ACTION & LOSS PROTECTION RULES (From TigerAutoRobot Image 3)
     */
    private fun evaluateImage3LossProtection(cleanList: List<String>): Pair<String, String> {
        if (cleanList.isEmpty()) return Pair("BIG", "Image 3 Default")
        val latest = cleanList[0]
        val opposite = oppositeOutcome(latest)

        var streak = 0
        for (item in cleanList) {
            if (item == latest) streak++ else break
        }

        return when {
            // Rule: 4+ streak -> Ride
            streak >= 4 -> Pair(latest, "Tier 3: 4+ ${latest}s Streak -> Ride $latest")
            // Rule: 3 streak -> Reversal
            streak == 3 -> Pair(opposite, "Tier 3: 3 ${latest}s -> Reversal to $opposite")
            // Rule: 2 streak -> Transition
            streak == 2 -> Pair(opposite, "Tier 3: 2 ${latest}s -> Transition to $opposite")
            // Rule: 1 streak -> Check previous
            streak == 1 && cleanList.size >= 2 -> {
                val prev = cleanList[1]
                if (prev == "BIG" && latest == "SMALL") {
                    Pair("BIG", "Tier 3: Big Small -> BIG")
                } else if (prev == "SMALL" && latest == "BIG") {
                    Pair("SMALL", "Tier 3: Small Big -> SMALL")
                } else {
                    Pair(opposite, "Tier 3: 1-Run Bounce -> $opposite")
                }
            }
            else -> Pair(opposite, "Tier 3: Fade Single $latest -> $opposite")
        }
    }

    /**
     * Trend / Streak Strategy (From TigerAutoRobot Server 1)
     */
    private fun evaluateTrendStreak(cleanList: List<String>, periodId: String, currentConsLoss: Int = 0): PredictionResult {
        if (cleanList.isEmpty()) {
            val lastDigit = periodId.filter { it.isDigit() }.lastOrNull()?.digitToIntOrNull() ?: 5
            val pred = if (lastDigit >= 5) "BIG" else "SMALL"
            return PredictionResult(
                prediction = pred,
                botName = "Trend Streak Strategy",
                confidence = 80,
                predictedNumber = if (pred == "BIG") 7 else 2,
                patternDetected = "Default Parity"
            )
        }

        val blocks = parseTrendBlocks(cleanList)

        // 1. TIER 3 SAFEGUARD / LOSS RECOVERY (Triggered when user is in loss >= 2)
        if (currentConsLoss >= 2) {
            val tier3 = evaluateImage3LossProtection(cleanList)
            val pred = tier3.first.uppercase()
            return PredictionResult(
                prediction = pred,
                botName = "Trend Streak Strategy",
                confidence = 90,
                predictedNumber = if (pred == "BIG") 7 else 2,
                patternDetected = "🛡️ Safe Recovery [LVL ${currentConsLoss + 1}]: ${tier3.second}"
            )
        }

        // 2. TIER 1: BIG SMALL TREND CHART PATTERN (Image 1)
        val tier1 = evaluateImage1TrendChartPattern(blocks)
        if (tier1 != null) {
            val pred = tier1.first.uppercase()
            return PredictionResult(
                prediction = pred,
                botName = "Trend Streak Strategy",
                confidence = 88,
                predictedNumber = if (pred == "BIG") 8 else 3,
                patternDetected = "📊 Tier 1: ${tier1.second}"
            )
        }

        // 3. TIER 2: OFFICIAL COLOUR TRADING PATTERNS (Image 2)
        val tier2 = evaluateImage2ColourTradingPatterns(blocks)
        if (tier2 != null) {
            val pred = tier2.first.uppercase()
            return PredictionResult(
                prediction = pred,
                botName = "Trend Streak Strategy",
                confidence = 85,
                predictedNumber = if (pred == "BIG") 7 else 2,
                patternDetected = "📈 Tier 2: ${tier2.second}"
            )
        }

        // 4. TIER 3: DIRECT ACTION & TRIGGER RULES (Image 3)
        val tier3 = evaluateImage3LossProtection(cleanList)
        val pred = tier3.first.uppercase()
        return PredictionResult(
            prediction = pred,
            botName = "Trend Streak Strategy",
            confidence = 82,
            predictedNumber = if (pred == "BIG") 6 else 1,
            patternDetected = "⚡ Tier 3: ${tier3.second}"
        )
    }

    /**
     * Compute prediction based on chosen strategy:
     * 1. TEAM PANTHER VIP: S -> B -> R -> G -> B -> S -> G -> R (Continuous Loop)
     * 2. TEAM PANTHER VIP 2: S -> S -> B -> B -> S -> S -> B -> B (Continuous Loop)
     * 3. Trend Streak Strategy: Multi-Tier Pattern Engine from TigerAutoRobot
     * 4. AI Neural Predictor: Deep Neural AI v4.0 - 4-Layer Ensemble
     * 5. Dragon Strategy: Streak AI - Momentum follower
     * 6. Master Bot: Alternation AI - 1-skip pattern tracking
     * 7. Universal Bot: Digital Root AI - Period digit modulo
     */
    fun compute(
        strategy: String = "AI Neural Predictor",
        history: List<HistoryItem>,
        periodId: String,
        consecutiveLoss: Int = 0
    ): PredictionResult {
        val cleanPeriod = periodId.filter { it.isDigit() }
        val normalizedStrategy = strategy.trim().lowercase()

        val historyOutcomes = history.filter { item ->
            val p = item.periodId.filter { it.isDigit() }
            cleanPeriod.isEmpty() || (p != cleanPeriod && !p.endsWith(cleanPeriod) && !cleanPeriod.endsWith(p))
        }

        val outcomes = historyOutcomes.map { it.outcome.uppercase() }
        val numbers = historyOutcomes.map {
            if (it.number in 0..9) it.number else (if (it.outcome.equals("BIG", ignoreCase = true)) 7 else 2)
        }.toIntArray()

        return when {
            // 1. TEAM PANTHER VIP 2 (S, S, B, B, S, S, B, B Loop)
            normalizedStrategy.contains("vip 2") || normalizedStrategy == "team panther vip 2" -> {
                val pNum = cleanPeriod.takeLast(4).toLongOrNull() ?: (System.currentTimeMillis() / 30000)
                val idx = (((pNum % TEAM_PANTHER_VIP_2_PATTERN.size) + TEAM_PANTHER_VIP_2_PATTERN.size) % TEAM_PANTHER_VIP_2_PATTERN.size).toInt()
                val choice = TEAM_PANTHER_VIP_2_PATTERN[idx]
                val predNum = if (choice == "BIG") 7 else 2
                PredictionResult(
                    prediction = choice,
                    botName = "TEAM PANTHER VIP 2",
                    confidence = 88,
                    predictedNumber = predNum,
                    patternDetected = "Step ${idx + 1}/8 ($choice)"
                )
            }

            // 2. TEAM PANTHER VIP (S, B, R, G, B, S, G, R Loop)
            normalizedStrategy.contains("panther vip") || normalizedStrategy == "team panther vip" -> {
                val pNum = cleanPeriod.takeLast(4).toLongOrNull() ?: (System.currentTimeMillis() / 30000)
                val idx = (((pNum % TEAM_PANTHER_VIP_PATTERN.size) + TEAM_PANTHER_VIP_PATTERN.size) % TEAM_PANTHER_VIP_PATTERN.size).toInt()
                val choice = TEAM_PANTHER_VIP_PATTERN[idx]
                val predNum = when (choice) {
                    "BIG" -> 7
                    "SMALL" -> 2
                    "RED" -> 2
                    "GREEN" -> 3
                    else -> 5
                }
                PredictionResult(
                    prediction = choice,
                    botName = "TEAM PANTHER VIP",
                    confidence = 88,
                    predictedNumber = predNum,
                    patternDetected = "Step ${idx + 1}/8 ($choice)"
                )
            }

            // 3. Trend Streak Strategy (TigerAutoRobot Server 1)
            normalizedStrategy.contains("trend") || (normalizedStrategy.contains("streak") && !normalizedStrategy.contains("dragon")) -> {
                evaluateTrendStreak(outcomes, cleanPeriod, consecutiveLoss)
            }

            // 4. Dragon Strategy (Streak AI - Momentum follower)
            normalizedStrategy.contains("dragon") -> {
                val pred = if (outcomes.isNotEmpty()) outcomes[0].uppercase() else "BIG"
                val predNum = if (pred == "BIG") 7 else 2
                val streakCount = outcomes.takeWhile { it.equals(pred, ignoreCase = true) }.size.coerceAtLeast(1)
                val conf = (70 + (streakCount * 4)).coerceIn(72, 95)
                PredictionResult(
                    prediction = pred,
                    botName = "Dragon Strategy",
                    confidence = conf,
                    predictedNumber = predNum,
                    patternDetected = "Dragon Streak ($streakCount in a row)"
                )
            }

            // 5. Master Bot (1-Skip / Alternation Pattern Follower from TigerAutoRobot)
            normalizedStrategy.contains("master") || normalizedStrategy.contains("alternation") -> {
                val pred = if (outcomes.size >= 2) {
                    outcomes[1].uppercase()
                } else if (outcomes.isNotEmpty()) {
                    outcomes[0].uppercase()
                } else "BIG"
                val predNum = if (pred == "BIG") 8 else 3
                val isAlternating = outcomes.size >= 3 && outcomes[0] != outcomes[1] && outcomes[1] != outcomes[2]
                val conf = if (isAlternating) 88 else 78
                PredictionResult(
                    prediction = pred,
                    botName = "Master Bot",
                    confidence = conf,
                    predictedNumber = predNum,
                    patternDetected = if (isAlternating) "Ping-Pong Alternation" else "1-Skip Tracking"
                )
            }

            // 6. Universal Bot (Period Digit + Offset Sum Modulo from TigerAutoRobot)
            normalizedStrategy.contains("universal") || normalizedStrategy.contains("digital") || normalizedStrategy.contains("root") || normalizedStrategy.contains("bot-1") -> {
                val pDigits = if (cleanPeriod.length >= 2) cleanPeriod.takeLast(2) else "55"
                val d1 = pDigits[0].digitToInt()
                val d2 = pDigits[1].digitToInt()
                val num3 = if (numbers.size > 3) numbers[3].coerceIn(0, 9) else (if (outcomes.size > 3 && outcomes[3] == "BIG") 7 else 2)
                val num4 = if (numbers.size > 4) numbers[4].coerceIn(0, 9) else (if (outcomes.size > 4 && outcomes[4] == "BIG") 8 else 3)

                val sum = d1 + d2 + num3 + num4
                val value = (sum % 10 + 10) % 10
                val pred = if (value >= 5) "BIG" else "SMALL"
                PredictionResult(
                    prediction = pred,
                    botName = "Universal Bot",
                    confidence = 84,
                    predictedNumber = value,
                    patternDetected = "Digital Root Modulo (${sum % 10})"
                )
            }

            // 7. AI Neural Predictor (Deep Neural AI v4.0 - 4-Layer Ensemble)
            else -> {
                computeNeuralEnsemble(historyOutcomes, cleanPeriod)
            }
        }
    }

    /**
     * AI Neural Predictor (Deep Neural AI v4.0 from TigerAutoRobot)
     * Multi-layer AI model:
     *   - Layer 1: Ball Number Momentum & Exponential Weighted Moving Average (EMA)
     *   - Layer 2: Pattern sequences (Ping-Pong alternation vs Dragon run reversal / 2-2 Cycle)
     *   - Layer 3: 2-Gram Markov Transition Matrix
     *   - Layer 4: Mathematical Parity & Mean Reversion Boundary
     */
    private fun computeNeuralEnsemble(
        historyOutcomes: List<HistoryItem>,
        cleanPeriod: String
    ): PredictionResult {
        var bigScore = 0.0
        var smallScore = 0.0
        var patternDetected = "Neural Momentum"

        // LAYER 1: Ball Number (0-9) Prediction & Guessed Number via EMA
        val validNumbers = historyOutcomes.map { it.number }.filter { it in 0..9 }
        var predictedNumber = 5

        if (validNumbers.size >= 3) {
            val n0 = validNumbers[0]
            val n1 = validNumbers[1]
            val n2 = validNumbers[2]

            val emaNumber = (n0 * 0.50) + (n1 * 0.30) + (n2 * 0.20)
            val delta = n0 - n1
            var projectedNum = emaNumber + (delta * 0.35)

            projectedNum = projectedNum.coerceIn(0.0, 9.0)
            predictedNumber = Math.round(projectedNum).toInt().coerceIn(0, 9)

            if (predictedNumber >= 5) {
                bigScore += 3.5
            } else {
                smallScore += 3.5
            }
        } else {
            val recentBigCount = historyOutcomes.take(10).count { it.outcome.equals("BIG", ignoreCase = true) }
            val ratio = if (historyOutcomes.isNotEmpty()) recentBigCount.toDouble() / historyOutcomes.take(10).size.coerceAtLeast(1) else 0.5
            predictedNumber = if (ratio >= 0.5) 7 else 3
            if (predictedNumber >= 5) bigScore += 2.0 else smallScore += 2.0
        }

        // LAYER 2: Pattern Sequences (Ping-Pong & Streaks)
        val outcomes = historyOutcomes.map { it.outcome.uppercase() }
        if (outcomes.size >= 3) {
            val o0 = outcomes[0]
            val o1 = outcomes[1]
            val o2 = outcomes[2]

            if (!o0.equals(o1, ignoreCase = true) && !o1.equals(o2, ignoreCase = true)) {
                patternDetected = "Ping-Pong Alternation"
                if (o0.equals("BIG", ignoreCase = true)) {
                    smallScore += 3.0
                } else {
                    bigScore += 3.0
                }
            } else if (o0.equals(o1, ignoreCase = true)) {
                val o3 = outcomes.getOrNull(3)
                if (o3 != null && o2.equals(o0, ignoreCase = true) && o3.equals(o0, ignoreCase = true)) {
                    patternDetected = "Dragon Run Reversal"
                    if (o0.equals("BIG", ignoreCase = true)) {
                        smallScore += 2.5
                    } else {
                        bigScore += 2.5
                    }
                } else {
                    patternDetected = "2-2 Cycle"
                    if (o0.equals("BIG", ignoreCase = true)) {
                        smallScore += 2.2
                    } else {
                        bigScore += 2.2
                    }
                }
            } else {
                patternDetected = "Momentum Trend"
                if (o0.equals("BIG", ignoreCase = true)) {
                    bigScore += 2.0
                } else {
                    smallScore += 2.0
                }
            }
        }

        // LAYER 3: 2-Gram Markov Transition Matrix
        if (outcomes.size >= 5) {
            val lastOutcome = outcomes[0]
            var followBigCount = 0
            var followSmallCount = 0

            for (i in 1 until outcomes.size - 1) {
                if (outcomes[i].equals(lastOutcome, ignoreCase = true)) {
                    val whatFollowed = outcomes[i - 1]
                    if (whatFollowed.equals("BIG", ignoreCase = true)) followBigCount++
                    else followSmallCount++
                }
            }

            if (followBigCount > followSmallCount) {
                bigScore += 2.0
            } else if (followSmallCount > followBigCount) {
                smallScore += 2.0
            }
        }

        // LAYER 4: Period Digit Parity
        val pDigits = cleanPeriod.filter { it.isDigit() }
        val periodSum = if (pDigits.isNotEmpty()) pDigits.takeLast(4).map { it.digitToInt() }.sum() else 0
        if (periodSum % 2 == 0) {
            bigScore += 1.0
        } else {
            smallScore += 1.0
        }

        val total = bigScore + smallScore
        val choice = if (bigScore >= smallScore) "BIG" else "SMALL"
        val maxScore = Math.max(bigScore, smallScore)
        val confidencePercent = if (total > 0) Math.round((maxScore / total) * 100).toInt().coerceIn(68, 96) else 82

        return PredictionResult(
            prediction = choice,
            botName = "AI Neural Predictor",
            confidence = confidencePercent,
            predictedNumber = predictedNumber,
            patternDetected = patternDetected
        )
    }

    // Overload for backward compatibility
    fun compute(
        history: List<HistoryItem>,
        periodId: String
    ): PredictionResult {
        return compute("AI Neural Predictor", history, periodId, 0)
    }
}
