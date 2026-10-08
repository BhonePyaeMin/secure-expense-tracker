package com.example.expenses.service;

import com.example.expenses.model.Category;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guesses a category from an expense title with simple keyword rules, e.g. "grab ride" gives TRANSPORT.
 * No AI model and no extra memory: just a list of words per category.
 * <p>
 * English keywords match whole words (plural "s"/"es" allowed). Thai is written without spaces,
 * so Thai keywords match anywhere in the text. Longer keywords count for more, so "water bill"
 * beats "water", and on a tie the keyword that appears first in the title wins.
 */
@Service
public class CategorySuggester {

    private static final Map<Category, List<String>> KEYWORDS = new EnumMap<>(Category.class);

    static {
        KEYWORDS.put(Category.FOOD, List.of(
                "food", "meal", "lunch", "dinner", "breakfast", "brunch", "snack", "dessert", "coffee", "cafe",
                "café", "tea", "milk tea", "bubble tea", "boba", "juice", "smoothie", "restaurant", "rice",
                "noodle", "pizza", "burger", "sushi", "ramen", "kfc", "mcdonald", "starbucks", "grocery",
                "groceries", "supermarket", "market", "7-eleven", "7-11", "lotus", "big c", "makro",
                "foodpanda", "lineman", "grabfood", "bakery", "fruit",
                "ข้าว", "กาแฟ", "ชานม", "ก๋วยเตี๋ยว", "อาหาร", "ขนม"));
        KEYWORDS.put(Category.TRANSPORT, List.of(
                "transport", "grab", "bolt", "taxi", "uber", "ride", "bts", "mrt", "arl", "airport link", "bus",
                "train", "metro", "ferry", "boat", "van", "motorbike", "motorcycle", "tuk tuk", "tuk-tuk",
                "songthaew", "fuel", "gas", "petrol", "diesel", "parking", "toll", "flight", "airline",
                "rabbit card", "commute",
                "รถเมล์", "แท็กซี่", "วินมอเตอร์ไซค์", "ค่าน้ำมัน", "ค่ารถ"));
        KEYWORDS.put(Category.RENT, List.of(
                "rent", "dorm", "dormitory", "condo", "apartment", "room", "electricity", "electric bill",
                "water bill", "internet", "wifi", "wi-fi", "landlord", "deposit", "utility", "utilities",
                "ค่าเช่า", "ค่าหอ", "ค่าไฟ"));
        KEYWORDS.put(Category.STUDY, List.of(
                "study", "book", "textbook", "notebook", "course", "tuition", "class", "lecture", "udemy",
                "coursera", "stationery", "pen", "pencil", "printing", "print", "photocopy", "exam", "school",
                "university", "library", "tutor", "seminar", "workshop",
                "หนังสือ", "ค่าเทอม", "เรียน"));
        KEYWORDS.put(Category.HEALTH, List.of(
                "health", "pharmacy", "medicine", "doctor", "hospital", "clinic", "dentist", "dental", "gym",
                "fitness", "vitamin", "checkup", "check-up", "insurance", "therapy", "physio", "watsons",
                "contact lens",
                "ร้านยา", "โรงพยาบาล", "คลินิก", "ฟิตเนส"));
        KEYWORDS.put(Category.FUN, List.of(
                "fun", "movie", "cinema", "netflix", "spotify", "youtube", "disney", "hbo", "game", "steam",
                "playstation", "nintendo", "concert", "party", "bar", "pub", "beer", "karaoke", "bowling", "trip",
                "travel", "hotel", "museum", "festival", "zoo", "hobby",
                "ดูหนัง", "คอนเสิร์ต", "เกม"));
    }

    private record Rule(Category category, Pattern pattern, int weight) {
    }

    private final List<Rule> rules = new ArrayList<>();

    public CategorySuggester() {
        KEYWORDS.forEach((category, keywords) -> keywords.forEach(keyword ->
                rules.add(new Rule(category, compile(keyword), keyword.length()))));
    }

    public Optional<Category> suggest(String text) {
        if (!StringUtils.hasText(text)) {
            return Optional.empty();
        }
        Map<Category, Integer> scores = new EnumMap<>(Category.class);
        Map<Category, Integer> firstSeen = new EnumMap<>(Category.class);
        for (Rule rule : rules) {
            Matcher matcher = rule.pattern().matcher(text);
            if (matcher.find()) {
                scores.merge(rule.category(), rule.weight(), Integer::sum);
                firstSeen.merge(rule.category(), matcher.start(), Math::min);
            }
        }
        return scores.keySet().stream()
                .max((a, b) -> scores.get(a).equals(scores.get(b))
                        ? Integer.compare(firstSeen.get(b), firstSeen.get(a))
                        : Integer.compare(scores.get(a), scores.get(b)));
    }

    private static Pattern compile(String keyword) {
        boolean ascii = keyword.chars().allMatch(c -> c < 128);
        if (!ascii) {
            return Pattern.compile(Pattern.quote(keyword));
        }
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(keyword) + "(?:s|es)?(?![\\p{L}\\p{N}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
