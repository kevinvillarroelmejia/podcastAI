package com.podcastai.account;

import java.time.Clock;
import java.time.LocalDate;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.podcastai.ai.PodcastMode;
import com.podcastai.config.AppProperties;
import com.podcastai.web.ApiException;

/**
 * Planes y límites: un resumen gratis por cuenta y, con el plan de 10 €/mes, 120 minutos al mes.
 */
@Service
public class AccountService {

    private static final Pattern ACCOUNT_ID = Pattern.compile("^[A-Za-z0-9-]{16,64}$");

    private final AccountRepository accounts;
    private final AppProperties props;
    private final Clock clock;

    public AccountService(AccountRepository accounts, AppProperties props) {
        this.accounts = accounts;
        this.props = props;
        this.clock = Clock.systemUTC();
    }

    public static String requireValidId(String rawId) {
        if (rawId == null || !ACCOUNT_ID.matcher(rawId).matches()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Identificación de dispositivo no válida.");
        }
        return rawId;
    }

    /** Devuelve la cuenta, creándola si es nueva y reiniciando los minutos si ha empezado otro mes. */
    public Account getOrCreate(String rawId) {
        String id = requireValidId(rawId);
        LocalDate today = LocalDate.now(clock);
        if (!accounts.existsById(id)) {
            try {
                accounts.saveAndFlush(new Account(id, today));
            } catch (DataIntegrityViolationException e) {
                // Otra petición la ha creado a la vez: seguimos con la existente.
            }
        }
        accounts.resetPeriodIfDue(id, today, today.minusMonths(1));
        Account account = accounts.findById(id).orElseThrow();
        if (props.devPremium() && account.getPlan() != Plan.PREMIUM) {
            account.setPlan(Plan.PREMIUM);
            account = accounts.save(account);
        }
        return account;
    }

    /**
     * Comprueba que la cuenta puede generar este podcast y reserva el cupo gratis si toca.
     *
     * @return true si se ha usado el podcast gratis
     */
    public boolean reserve(Account account, PodcastMode mode) {
        boolean usedFree = false;
        if (account.getPlan() == Plan.PREMIUM) {
            double left = monthlyLimit(account) - account.getMinutesUsed();
            if (left < mode.estimatedMinutes()) {
                throw new ApiException(HttpStatus.PAYMENT_REQUIRED,
                        "Te quedan " + Math.max(0, Math.round(left)) + " minutos este mes y este podcast puede durar hasta "
                                + mode.estimatedMinutes() + ".");
            }
        } else {
            if (mode != PodcastMode.RESUMEN) {
                throw new ApiException(HttpStatus.PAYMENT_REQUIRED,
                        "El modo A fondo es del plan Premium. El podcast gratis es un resumen.");
            }
            int claimed = accounts.claimFreePodcast(account.getId(), props.limits().freePodcasts());
            if (claimed == 0) {
                throw new ApiException(HttpStatus.PAYMENT_REQUIRED,
                        "Ya has usado tu podcast gratis. Hazte Premium para seguir escuchando.");
            }
            usedFree = true;
        }
        return usedFree;
    }

    public void refundFree(String accountId) {
        accounts.refundFreePodcast(accountId);
    }

    public void addMinutes(String accountId, double minutes) {
        accounts.addMinutes(accountId, minutes);
    }

    public int monthlyLimit(Account account) {
        return account.getPlan() == Plan.PREMIUM ? props.limits().monthlyMinutesPremium() : 0;
    }

    public void setPlan(String rawId, Plan plan) {
        Account account = getOrCreate(rawId);
        account.setPlan(plan);
        accounts.save(account);
    }

    public void delete(String accountId) {
        if (accounts.existsById(accountId)) {
            accounts.deleteById(accountId);
        }
    }

    public Account find(String rawId) {
        return getOrCreate(rawId);
    }
}
