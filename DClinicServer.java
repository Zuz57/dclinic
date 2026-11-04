import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class DClinicServer {

    public static void main(String[] args) throws IOException {
        HttpServer server = null;
        int port = 8081;

        // Автоматический поиск свободного порта, чтобы избежать BindException
        while (port <= 8095) {
            try {
                server = HttpServer.create(new InetSocketAddress(port), 0);
                break;
            } catch (BindException e) {
                port++;
            }
        }

        if (server == null) {
            System.err.println("Не удалось найти свободный порт в диапазоне 8081-8095.");
            return;
        }

        server.createContext("/", new StaticHandler());
        server.createContext("/api/process", new ClinicHandler());

        server.setExecutor(null);
        System.out.println("============================================================");
        System.out.println(" Клиентский портал клиники DClinic успешно запущен!");
        System.out.println(" Откройте в браузере: http://localhost:" + port);
        System.out.println("============================================================");
        server.start();
    }

    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Path filePath = Path.of("index.html");
            if (!Files.exists(filePath)) {
                String error = "Файл index.html не найден в папке проекта!";
                byte[] bytes = error.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }
            byte[] bytes = Files.readAllBytes(filePath);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    static class ClinicHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> params = parseFormData(body);

            String jsonResponse = evaluateClinicLogic(params);
            byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }

        private String evaluateClinicLogic(Map<String, String> p) {
            // ЗАДАЧА 1: Главное меню системы и список доступных разделов
            String systemMenu = "Портал клиники DClinic | Разделы: 1. Главная и профиль, 2. Онлайн-диагностика, "
                    + "3. Программа лояльности и рассрочка, 4. Услуги и калькулятор, 5. Итоговая медкарта";

            String login = p.getOrDefault("login", "").trim();
            String password = p.getOrDefault("password", "").trim();
            String patientName = p.getOrDefault("patientName", "").trim();
            String userStatus = p.getOrDefault("userStatus", "ACTIVE");
            String sectionStr = p.getOrDefault("section", "1");
            String serviceCategory = p.getOrDefault("serviceCategory", "CARDIO");
            String tariff = p.getOrDefault("tariff", "STANDARD");
            boolean hasInsurance = Boolean.parseBoolean(p.getOrDefault("hasInsurance", "false"));
            boolean isVipCard = Boolean.parseBoolean(p.getOrDefault("isVipCard", "false"));
            boolean isUrgent = Boolean.parseBoolean(p.getOrDefault("isUrgent", "false"));
            boolean includeLabs = Boolean.parseBoolean(p.getOrDefault("includeLabs", "false"));
            boolean hadInfectionContact = Boolean.parseBoolean(p.getOrDefault("hadInfectionContact", "false"));

            // ЗАДАЧА 2: Проверка введённых данных (пустые поля и корректность чисел)
            int age, heartRate, spo2, section;
            double glucose, purchaseAmount, income, expenses, healthScore, temperature, systolicBp;

            if (patientName.isEmpty() || login.isEmpty() || password.isEmpty()) {
                return buildErrorJson("Пожалуйста, укажите ФИО пациента, логин и пароль в Личном кабинете.", systemMenu);
            }

            try {
                age = Integer.parseInt(p.getOrDefault("age", "0"));
                glucose = Double.parseDouble(p.getOrDefault("glucose", "0"));
                purchaseAmount = Double.parseDouble(p.getOrDefault("purchaseAmount", "0"));
                income = Double.parseDouble(p.getOrDefault("income", "0"));
                expenses = Double.parseDouble(p.getOrDefault("expenses", "0"));
                healthScore = Double.parseDouble(p.getOrDefault("healthScore", "0"));
                temperature = Double.parseDouble(p.getOrDefault("temperature", "36.6"));
                systolicBp = Double.parseDouble(p.getOrDefault("systolicBp", "120"));
                heartRate = Integer.parseInt(p.getOrDefault("heartRate", "70"));
                spo2 = Integer.parseInt(p.getOrDefault("spo2", "98"));
                section = Integer.parseInt(sectionStr);
            } catch (NumberFormatException e) {
                return buildErrorJson("В одно из числовых полей введено некорректное значение. Проверьте заполнение анкеты.", systemMenu);
            }

            if (age <= 0 || age > 125 || glucose <= 0 || purchaseAmount < 0 || income < 0 || expenses < 0
                    || healthScore < 0 || healthScore > 100 || temperature < 34.0 || temperature > 43.0
                    || heartRate < 25 || heartRate > 240 || spo2 < 50 || spo2 > 100) {
                return buildErrorJson("Указаны недопустимые показатели (проверьте возраст, температуру 34–43°C, пульс, сатурацию или суммы).", systemMenu);
            }

            // ЗАДАЧА 5: Проверка логина и пароля
            boolean accessGranted;
            String authMessage;
            if (login.equals("admin") && password.equals("dclinic2026")) {
                accessGranted = true;
                authMessage = "Авторизация успешна: электронная медкарта пациента синхронизирована.";
            } else {
                accessGranted = false;
                authMessage = "Неверный логин или пароль от Личного кабинета! (Демо-доступ: admin / dclinic2026)";
                return buildErrorJson(authMessage, systemMenu);
            }

            // ЗАДАЧА 3: Определение статуса пользователя (if-else)
            String statusDescription;
            String statusBadge;
            boolean isBlocked = false;
            if (userStatus.equals("ACTIVE")) {
                statusDescription = "Карта активна — доступны онлайн-запись, бонусы и ДМС";
                statusBadge = "Активный пациент";
            } else if (userStatus.equals("INACTIVE")) {
                statusDescription = "Карта временно неактивна — требуется подтверждение данных на ресепшене";
                statusBadge = "Неактивный профиль";
            } else {
                statusDescription = "Обслуживание приостановлено — обратитесь в службу поддержки клиники";
                statusBadge = "Заблокирован";
                isBlocked = true;
            }

            // ЗАДАЧА 9: Логический оператор ! для проверки отсутствия блокировки
            String negativeCheckMsg;
            if (!isBlocked) {
                negativeCheckMsg = "Доступ к онлайн-записи открыт (блокировки отсутствуют).";
            } else {
                negativeCheckMsg = "Онлайн-запись ограничена из-за блокировки карты пациента.";
            }

            // ЗАДАЧА 4: Проверка числового показателя (уровень глюкозы — 3 варианта)
            String glucoseResult;
            String glucoseState;
            if (glucose < 3.9) {
                glucoseResult = "Пониженный сахар (" + glucose + " ммоль/л — гипогликемия)";
                glucoseState = "LOW";
            } else if (glucose <= 5.5) {
                glucoseResult = "В пределах нормы (" + glucose + " ммоль/л — референс 3.9–5.5)";
                glucoseState = "NORMAL";
            } else {
                glucoseResult = "Повышенный сахар (" + glucose + " ммоль/л — требуется контроль эндокринолога)";
                glucoseState = "HIGH";
            }

            // ЗАДАЧА 6: Возрастная классификация if-else if (4 категории)
            String ageCategory;
            if (age < 12) {
                ageCategory = "Детское отделение (Педиатрия, 0–11 лет)";
            } else if (age < 18) {
                ageCategory = "Подростковое отделение (12–17 лет)";
            } else if (age < 60) {
                ageCategory = "Взрослое отделение (Терапия и узкие специалисты, 18–59 лет)";
            } else {
                ageCategory = "Гериатрический центр (Забота о старшем поколении, 60+ лет)";
            }

            // ЗАДАЧА 7: Одновременная проверка условий через &&
            String dualRequirementMsg;
            if (age >= 18 && hasInsurance) {
                dualRequirementMsg = "Одобрено прямое прикрепление по полису ДМС (пациент совершеннолетний и полис активен).";
            } else {
                dualRequirementMsg = "Прямое списание по ДМС недоступно (требуется возраст 18+ и активный страховой полис).";
            }

            // ЗАДАЧА 8: Альтернативные условия через ||
            String priorityStatusMsg;
            boolean hasPrivilege = false;
            if (age >= 60 || isVipCard || purchaseAmount >= 50000) {
                hasPrivilege = true;
                priorityStatusMsg = "Приоритетное обслуживание Fast-Track (активировано по возрасту 60+, VIP-карте или сумме чека от 50 000 ₸).";
            } else {
                priorityStatusMsg = "Стандартная очередь записи по времени.";
            }

            // ЗАДАЧА 10: Вложенные if (минимум 3 последовательных проверки)
            String nestedDecision;
            if (accessGranted) {
                if (!isBlocked) {
                    if (temperature <= 37.2) {
                        if (systolicBp <= 140) {
                            nestedDecision = "Допуск разрешён: пациент может проходить плановые процедуры и манипуляции.";
                        } else {
                            nestedDecision = "Внимание: повышенное АД (" + systolicBp + " мм рт.ст.) — перед процедурой требуется осмотр кардиолога.";
                        }
                    } else {
                        nestedDecision = "Внимание: температура " + temperature + "°C — приём проводится через отдельный фильтр-бокс.";
                    }
                } else {
                    nestedDecision = "Отказ в плановой записи: профиль заблокирован.";
                }
            } else {
                nestedDecision = "Требуется авторизация.";
            }

            // ЗАДАЧА 11: Система расчёта скидок (от суммы и статуса клиента)
            double discountPercent = 0.0;
            if (userStatus.equals("ACTIVE") && isVipCard) {
                if (purchaseAmount >= 50000) {
                    discountPercent = 20.0;
                } else {
                    discountPercent = 15.0;
                }
            } else if (userStatus.equals("ACTIVE")) {
                if (purchaseAmount >= 50000) {
                    discountPercent = 10.0;
                } else if (purchaseAmount >= 20000) {
                    discountPercent = 5.0;
                }
            }
            double discountedTotal = purchaseAmount * (1.0 - discountPercent / 100.0);
            String discountMsg = String.format("Ваша скидка: %.0f%% • К оплате по плану лечения: %.0f ₸ (экономия %.0f ₸)",
                    discountPercent, discountedTotal, (purchaseAmount - discountedTotal));

            // ЗАДАЧА 12: Анализ доходов и расходов (финансовый статус и рассрочка)
            double netBalance = income - expenses;
            String financialStatus;
            String installmentBadge;
            if (netBalance >= discountedTotal * 2) {
                financialStatus = "Высокая платёжеспособность: комфортная разовая оплата или премиальный депозит.";
                installmentBadge = "Доступна любая форма оплаты";
            } else if (netBalance >= discountedTotal) {
                financialStatus = "Стабильный бюджет: свободного остатка средств (" + String.format("%.0f", netBalance) + " ₸) достаточно для оплаты.";
                installmentBadge = "Доступна оплата и рассрочка 0%";
            } else if (netBalance > 0) {
                double monthly = discountedTotal / 6.0;
                financialStatus = String.format("Рекомендована рассрочка DClinic 0%% на 6 мес. (платёж всего %.0f ₸/мес без переплат).", monthly);
                installmentBadge = "Одобрена рассрочка 0% на 6 мес.";
            } else {
                financialStatus = "Расходы превышают доходы: доступна социальная программа отсрочки платежа и квота клиники.";
                installmentBadge = "Доступна социальная квота";
            }

            // ЗАДАЧА 13: Оценка результатов чекапа по баллам
            String healthEvaluation;
            if (healthScore >= 85) {
                healthEvaluation = "Индекс здоровья A (Отлично, " + (int) healthScore + "/100): организм в прекрасной форме.";
            } else if (healthScore >= 70) {
                healthEvaluation = "Индекс здоровья B (Хорошо, " + (int) healthScore + "/100): умеренная утомляемость, рекомендуется витаминотерапия.";
            } else if (healthScore >= 50) {
                healthEvaluation = "Индекс здоровья C (Удовлетворительно, " + (int) healthScore + "/100): рекомендуется базовый чекап и коррекция режима.";
            } else {
                healthEvaluation = "Индекс здоровья D (Требует внимания, " + (int) healthScore + "/100): необходим комплексный осмотр терапевта.";
            }

            // ЗАДАЧА 14: Проверка эпидемиологической и медицинской безопасности
            String safetyRiskLevel;
            if ((temperature >= 38.0 && hadInfectionContact) || spo2 < 92) {
                safetyRiskLevel = "Высокий эпид/мед риск: требуется приём в изолированном боксе и экстренный контроль сатурации.";
            } else if (temperature >= 37.3 || hadInfectionContact || spo2 < 95) {
                safetyRiskLevel = "Средний уровень риска: рекомендован экспресс-тест и консультация дежурного терапевта.";
            } else {
                safetyRiskLevel = "Безопасный уровень: эпидемиологических ограничений для посещения клиники нет.";
            }

            // ЗАДАЧА 15: Базовая система рекомендаций
            String baseRecommendation;
            if (age >= 50 && systolicBp > 135) {
                baseRecommendation = "Рекомендуем пройти ЭКГ, ЭхоКГ и суточный мониторинг давления в отделении кардиологии.";
            } else if (glucose > 5.5) {
                baseRecommendation = "Рекомендуем сдать анализ на гликированный гемоглобин (HbA1c) и посетить эндокринолога.";
            } else if (healthScore < 65) {
                baseRecommendation = "Рекомендуем программу «Восстановление энергии» и расширенный биохимический анализ крови.";
            } else {
                baseRecommendation = "Показатели стабильны — достаточно ежегодного профилактического осмотра.";
            }

            // ЗАДАЧА 16 и ЗАДАЧА 19: Переключение разделов (switch) + обработка ошибок
            String activeSectionInfo;
            switch (section) {
                case 1:
                    activeSectionInfo = "Раздел 1: Главная страница и управление профилем пациента";
                    break;
                case 2:
                    activeSectionInfo = "Раздел 2: Центр онлайн-диагностики и биометрического скрининга";
                    break;
                case 3:
                    activeSectionInfo = "Раздел 3: Бонусная программа и расчёт медицинской рассрочки";
                    break;
                case 4:
                    activeSectionInfo = "Раздел 4: Каталог отделений клиники и калькулятор стоимости приёма";
                    break;
                case 5:
                    activeSectionInfo = "Раздел 5: Персональная электронная медкарта и итоговое заключение";
                    break;
                default:
                    activeSectionInfo = "Ошибка навигации (Задача 19): Раздел №" + section + " не существует. Выберите раздел от 1 до 5.";
                    break;
            }

            // ЗАДАЧА 17 и ЗАДАЧА 19: Выбор категории услуги + возможности отделения
            String categoryTitle;
            String categoryCapabilities;
            String doctorName;
            double baseServicePrice;
            switch (serviceCategory) {
                case "THERAPY":
                    categoryTitle = "Терапевтическое отделение";
                    categoryCapabilities = "Первичный и повторный осмотр, оформление больничных листов, направление на анализы, чекап ОРВИ.";
                    doctorName = "Д-р Смагулова Алия Маратовна (Терапевт высшей категории)";
                    baseServicePrice = 10000;
                    break;
                case "DENTAL":
                    categoryTitle = "Цифровая стоматология";
                    categoryCapabilities = "3D-томография челюсти, безболезненное лечение кариеса под микроскопом, чистка AirFlow, имплантация.";
                    doctorName = "Д-р Ким Тимур Викторович (Стоматолог-хирург, ортопед)";
                    baseServicePrice = 25000;
                    break;
                case "CARDIO":
                    categoryTitle = "Центр кардиологии";
                    categoryCapabilities = "ЭКГ в 12 отведениях, УЗИ сердца (ЭхоКГ) экспертного класса, Холтер-мониторинг, подбор терапии АД.";
                    doctorName = "Д-р Ибраев Нурлан Серикович (Кардиолог, к.м.н.)";
                    baseServicePrice = 20000;
                    break;
                case "SURGERY":
                    categoryTitle = "Амбулаторная хирургия";
                    categoryCapabilities = "Малоинвазивные операции одного дня, лазерная хирургия, перевязки, предоперационный консилиум.";
                    doctorName = "Д-р Оспанов Ермек Болатович (Ведущий хирург)";
                    baseServicePrice = 45000;
                    break;
                case "CHECKUP":
                    categoryTitle = "Комплексный Check-up организма";
                    categoryCapabilities = "Полное обследование за 3 часа: 25 лабораторных показателей, УЗИ брюшной полости и щитовидной железы, МРТ.";
                    doctorName = "Д-р Бекова Динара Аскаровна (Куратор превентивной медицины)";
                    baseServicePrice = 60000;
                    break;
                default:
                    categoryTitle = "Неизвестное отделение";
                    categoryCapabilities = "Ошибка выбора категории услуги (Задача 19). Пожалуйста, выберите отделение из списка.";
                    doctorName = "Дежурный специалист";
                    baseServicePrice = 0;
                    break;
            }

            // ЗАДАЧА 18: Калькулятор стоимости услуги (тариф + дополнительные опции)
            double tariffMultiplier;
            String tariffName;
            switch (tariff) {
                case "BASIC":
                    tariffMultiplier = 1.0;
                    tariffName = "Базовый приём (x1.0)";
                    break;
                case "STANDARD":
                    tariffMultiplier = 1.3;
                    tariffName = "Стандарт Комфорт (x1.3)";
                    break;
                case "VIP":
                    tariffMultiplier = 1.8;
                    tariffName = "VIP Персональное сопровождение (x1.8)";
                    break;
                default:
                    tariffMultiplier = 1.0;
                    tariffName = "Базовый приём (x1.0)";
                    break;
            }

            double calculatedServiceCost = baseServicePrice * tariffMultiplier;
            if (isUrgent) {
                calculatedServiceCost += 8000;
            }
            if (includeLabs) {
                calculatedServiceCost += 12000;
            }
            if (hasInsurance) {
                calculatedServiceCost *= 0.7;
            }
            String calculatorMsg = String.format("Тариф «%s» • Базовая цена: %.0f ₸ • Итого с выбранными опциями: %.0f ₸",
                    tariffName, baseServicePrice, calculatedServiceCost);

            // ЗАДАЧА 20: Комбинация switch, if-else и логических операторов (5 клинических сценариев)
            String combinedScenarioMsg;
            switch (serviceCategory) {
                case "SURGERY":
                    if (!isBlocked && age >= 18 && temperature <= 37.0 && systolicBp <= 140) {
                        combinedScenarioMsg = "Готовность к манипуляции: пациент полностью допущен к плановому хирургическому приёму.";
                    } else {
                        combinedScenarioMsg = "Требуется подготовка: плановая операция отложена до нормализации давления/температуры или подтверждения статуса.";
                    }
                    break;
                case "CARDIO":
                    if ((systolicBp >= 140 || heartRate > 100) && !isBlocked) {
                        combinedScenarioMsg = "Ускоренный маршрут: выявлено повышенное давление или пульс — приём кардиолога организован без очереди.";
                    } else {
                        combinedScenarioMsg = "Плановый маршрут: запись на кардиологическую диагностику в удобное время.";
                    }
                    break;
                case "DENTAL":
                    if (!hadInfectionContact && temperature < 37.3) {
                        combinedScenarioMsg = "Стандартный допуск: пациент записан в стерильный стоматологический кабинет.";
                    } else {
                        combinedScenarioMsg = "Особый режим: при признаках простуды оказывается только неотложная стоматологическая помощь в отдельном кабинете.";
                    }
                    break;
                case "CHECKUP":
                    if (hasPrivilege || hasInsurance) {
                        combinedScenarioMsg = "Привилегированный Check-up: выделена индивидуальная палата отдыха и персональный медсестринский пост.";
                    } else {
                        combinedScenarioMsg = "Стандартный Check-up: прохождение всех специалистов по единому маршрутному листу за 1 утро.";
                    }
                    break;
                default:
                    if (!isBlocked && (temperature >= 37.5 || spo2 < 95)) {
                        combinedScenarioMsg = "Неотложная терапия: организован первоочередной осмотр дежурным врачом-терапевтом.";
                    } else {
                        combinedScenarioMsg = "Плановая терапия: стандартная консультация и составление плана лечения.";
                    }
                    break;
            }

            // ЗАДАЧА 21 и ЗАДАЧА 22: Индивидуальный модуль DClinic Smart Triage (анализ 5+ параметров)
            int riskPoints = 0;
            StringBuilder triageFactors = new StringBuilder();

            if (temperature >= 38.5) {
                riskPoints += 3;
                triageFactors.append("Высокая температура (").append(temperature).append("°C); ");
            } else if (temperature >= 37.2) {
                riskPoints += 1;
                triageFactors.append("Субфебрильная температура (").append(temperature).append("°C); ");
            }

            if (heartRate > 110 || heartRate < 50) {
                riskPoints += 2;
                triageFactors.append("Отклонение пульса (").append(heartRate).append(" уд/мин); ");
            }

            if (systolicBp >= 160 || systolicBp < 90) {
                riskPoints += 3;
                triageFactors.append("Критическое АД (").append((int) systolicBp).append(" мм рт.ст.); ");
            } else if (systolicBp >= 140) {
                riskPoints += 1;
                triageFactors.append("Повышенное АД (").append((int) systolicBp).append(" мм рт.ст.); ");
            }

            if (spo2 < 93) {
                riskPoints += 4;
                triageFactors.append("Сниженная сатурация SpO2 (").append(spo2).append("%); ");
            } else if (spo2 < 96) {
                riskPoints += 1;
                triageFactors.append("Пограничная сатурация (").append(spo2).append("%); ");
            }

            if ((age >= 65 && glucose > 6.5) || glucose > 8.5) {
                riskPoints += 2;
                triageFactors.append("Гликемический фактор риска (сахар ").append(glucose).append(" ммоль/л); ");
            }

            if (triageFactors.length() == 0) {
                triageFactors.append("Все 5 жизненных показателей в пределах физиологической нормы.");
            }

            // ЗАДАЧА 23: Три уровня медицинского триажа (Низкий, Средний, Высокий)
            String triageLevel;
            String triageColor;
            if (riskPoints >= 5) {
                triageLevel = "Высокий приоритет (Экстренный осмотр врача)";
                triageColor = "RED";
            } else if (riskPoints >= 2) {
                triageLevel = "Средний приоритет (Планово-ускоренный приём)";
                triageColor = "AMBER";
            } else {
                triageLevel = "Низкий риск (Плановое профилактическое наблюдение)";
                triageColor = "GREEN";
            }

            // ЗАДАЧА 24: Персонализированные врачебные рекомендации по комбинации условий
            String personalRecommendation;
            if (riskPoints >= 5 && (spo2 < 93 || systolicBp >= 160)) {
                personalRecommendation = "Рекомендовано немедленное обращение в приёмный покой DClinic или вызов мобильной бригады клиники для стабилизации показателей.";
            } else if (riskPoints >= 2 && glucose > 5.5 && systolicBp >= 140) {
                personalRecommendation = "Рекомендован комплексный приём кардиолога и эндокринолога, контроль сахара натощак и ведение дневника артериального давления.";
            } else if (riskPoints >= 2) {
                personalRecommendation = "Рекомендована очная консультация профильного специалиста в ближайшие 24–48 часов и сдача общего анализа крови.";
            } else {
                personalRecommendation = "Ваши жизненные показатели в норме! Рекомендуем поддерживать здоровый режим сна, питьевой баланс и проходить профилактический Check-up раз в год.";
            }

            // ЗАДАЧА 25: Итоговое резюме системы
            String finalSummary = String.format(
                    "Пациент: %s (%s) • Выбрано направление: %s (%s) • Стоимость приёма: %.0f ₸ • Статус триажа: %s.",
                    patientName, ageCategory, categoryTitle, doctorName, calculatedServiceCost, triageLevel
            );

            StringBuilder json = new StringBuilder("{");
            json.append("\"success\":true,");
            json.append("\"patientName\":\"").append(escape(patientName)).append("\",");
            json.append("\"statusBadge\":\"").append(escape(statusBadge)).append("\",");
            json.append("\"categoryTitle\":\"").append(escape(categoryTitle)).append("\",");
            json.append("\"doctorName\":\"").append(escape(doctorName)).append("\",");
            json.append("\"calculatedCost\":\"").append(String.format("%.0f ₸", calculatedServiceCost)).append("\",");
            json.append("\"discountedTotal\":\"").append(String.format("%.0f ₸", discountedTotal)).append("\",");
            json.append("\"discountPercent\":\"").append(String.format("%.0f%%", discountPercent)).append("\",");
            json.append("\"installmentBadge\":\"").append(escape(installmentBadge)).append("\",");
            json.append("\"triageColor\":\"").append(escape(triageColor)).append("\",");
            json.append("\"glucoseState\":\"").append(escape(glucoseState)).append("\",");
            json.append("\"task1_menu\":\"").append(escape(systemMenu)).append("\",");
            json.append("\"task3_status\":\"").append(escape(statusDescription)).append("\",");
            json.append("\"task4_glucose\":\"").append(escape(glucoseResult)).append("\",");
            json.append("\"task5_auth\":\"").append(escape(authMessage)).append("\",");
            json.append("\"task6_age\":\"").append(escape(ageCategory)).append("\",");
            json.append("\"task7_and\":\"").append(escape(dualRequirementMsg)).append("\",");
            json.append("\"task8_or\":\"").append(escape(priorityStatusMsg)).append("\",");
            json.append("\"task9_not\":\"").append(escape(negativeCheckMsg)).append("\",");
            json.append("\"task10_nested\":\"").append(escape(nestedDecision)).append("\",");
            json.append("\"task11_discount\":\"").append(escape(discountMsg)).append("\",");
            json.append("\"task12_finance\":\"").append(escape(financialStatus)).append("\",");
            json.append("\"task13_score\":\"").append(escape(healthEvaluation)).append("\",");
            json.append("\"task14_safety\":\"").append(escape(safetyRiskLevel)).append("\",");
            json.append("\"task15_baseRec\":\"").append(escape(baseRecommendation)).append("\",");
            json.append("\"task16_section\":\"").append(escape(activeSectionInfo)).append("\",");
            json.append("\"task17_category\":\"").append(escape(categoryCapabilities)).append("\",");
            json.append("\"task18_calculator\":\"").append(escape(calculatorMsg)).append("\",");
            json.append("\"task20_combined\":\"").append(escape(combinedScenarioMsg)).append("\",");
            json.append("\"task21_22_analysis\":\"").append(escape("Индекс риска: " + riskPoints + " балл(ов) • " + triageFactors)).append("\",");
            json.append("\"task23_level\":\"").append(escape(triageLevel)).append("\",");
            json.append("\"task24_personalRec\":\"").append(escape(personalRecommendation)).append("\",");
            json.append("\"task25_summary\":\"").append(escape(finalSummary)).append("\"");
            json.append("}");
            return json.toString();
        }

        private String buildErrorJson(String message, String menu) {
            return "{"
                    + "\"success\":false,"
                    + "\"errorMessage\":\"" + escape(message) + "\","
                    + "\"task1_menu\":\"" + escape(menu) + "\""
                    + "}";
        }

        private Map<String, String> parseFormData(String body) {
            Map<String, String> map = new HashMap<>();
            if (body == null || body.isEmpty()) return map;
            for (String pair : body.split("&")) {
                String[] kv = pair.split("=", 2);
                String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                String val = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
                map.put(key, val);
            }
            return map;
        }

        private String escape(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
        }
    }
}