package ee.taltech.inbankbackend.service;

import com.github.vladislavgoltjajev.personalcode.locale.estonia.EstonianPersonalCodeValidator;
import ee.taltech.inbankbackend.config.DecisionEngineConstants;
import ee.taltech.inbankbackend.exceptions.*;
import org.springframework.stereotype.Service;

import java.time.*;

/**
 * A service class that provides a method for calculating an approved loan amount and period for a customer.
 * The loan amount is calculated based on the customer's credit modifier,
 * which is determined by the last four digits of their ID code.
 */
@Service
public class DecisionEngine {

    // Used to check for the validity of the presented ID code.
    private final EstonianPersonalCodeValidator validator = new EstonianPersonalCodeValidator();
    private int creditModifier = 0;

    /**
     * Calculates the maximum loan amount and period for the customer based on their ID code,
     * the requested loan amount and the loan period.
     * The loan period must be between 12 and 60 months (inclusive).
     * The loan amount must be between 2000 and 10000€ months (inclusive).
     *
     * @param personalCode ID code of the customer that made the request.
     * @param loanAmount Requested loan amount
     * @param loanPeriod Requested loan period
     * @return A Decision object containing the approved loan amount and period, and an error message (if any)
     * @throws InvalidPersonalCodeException If the provided personal ID code is invalid
     * @throws InvalidLoanAmountException If the requested loan amount is invalid
     * @throws InvalidLoanPeriodException If the requested loan period is invalid
     * @throws NoValidLoanException If there is no valid loan found for the given ID code, loan amount and loan period
     */
    public Decision calculateApprovedLoan(String personalCode, Long loanAmount, int loanPeriod)
            throws InvalidPersonalCodeException, InvalidLoanAmountException, InvalidLoanPeriodException,
            NoValidLoanException, InvalidAgeException {
        try {
            verifyInputs(personalCode, loanAmount, loanPeriod);
        } catch (Exception e) {
            return new Decision(null, null, e.getMessage());
        }

        verifyAge(personalCode, loanPeriod);

        int outputLoanAmount;
        creditModifier = getCreditModifier(personalCode);

        if (creditModifier == 0) {
            throw new NoValidLoanException("No valid loan found!");
        }

        outputLoanAmount = loanAmount.intValue();

        while (getCreditScore(outputLoanAmount, loanPeriod) < DecisionEngineConstants.MINIMUM_CREDIT_SCORE_TO_APPROVE &&
                loanAmount > DecisionEngineConstants.MINIMUM_LOAN_AMOUNT) {
            outputLoanAmount -= 100;
        }

        while (getCreditScore(outputLoanAmount, loanPeriod) < DecisionEngineConstants.MINIMUM_CREDIT_SCORE_TO_APPROVE &&
                loanPeriod < DecisionEngineConstants.MAXIMUM_LOAN_PERIOD) {
            loanPeriod++;
        }

        return new Decision(outputLoanAmount, loanPeriod, null);
    }

    /**
     * Calculates the credit score for the person taking a loan.
     *
     * @param loanAmount Requested loan amount
     * @param loanPeriod Requested loan period
     * @return The credit score for given arguments
     */
    private double getCreditScore(int loanAmount, int loanPeriod) {
        return ((double) creditModifier / (double) loanAmount) * loanPeriod / 10;
    }

    /**
     * Calculates the credit modifier of the customer to according to the last four digits of their ID code.
     * Debt - 0000...2499
     * Segment 1 - 2500...4999
     * Segment 2 - 5000...7499
     * Segment 3 - 7500...9999
     *
     * @param personalCode ID code of the customer that made the request.
     * @return Segment to which the customer belongs.
     */
    private int getCreditModifier(String personalCode) {
        int segment = Integer.parseInt(personalCode.substring(personalCode.length() - 4));

        if (segment < 2500) {
            return 0;
        } else if (segment < 5000) {
            return DecisionEngineConstants.SEGMENT_1_CREDIT_MODIFIER;
        } else if (segment < 7500) {
            return DecisionEngineConstants.SEGMENT_2_CREDIT_MODIFIER;
        }

        return DecisionEngineConstants.SEGMENT_3_CREDIT_MODIFIER;
    }

    /**
     * Verify that all inputs are valid according to business rules.
     * If inputs are invalid, then throws corresponding exceptions.
     *
     * @param personalCode Provided personal ID code
     * @param loanAmount Requested loan amount
     * @param loanPeriod Requested loan period
     * @throws InvalidPersonalCodeException If the provided personal ID code is invalid
     * @throws InvalidLoanAmountException If the requested loan amount is invalid
     * @throws InvalidLoanPeriodException If the requested loan period is invalid
     */
    private void verifyInputs(String personalCode, Long loanAmount, int loanPeriod)
            throws InvalidPersonalCodeException, InvalidLoanAmountException, InvalidLoanPeriodException {

        if (!validator.isValid(personalCode)) {
            throw new InvalidPersonalCodeException("Invalid personal ID code!");
        }
        if (!(DecisionEngineConstants.MINIMUM_LOAN_AMOUNT <= loanAmount)
                || !(loanAmount <= DecisionEngineConstants.MAXIMUM_LOAN_AMOUNT)) {
            throw new InvalidLoanAmountException("Invalid loan amount!");
        }
        if (!(DecisionEngineConstants.MINIMUM_LOAN_PERIOD <= loanPeriod)
                || !(loanPeriod <= DecisionEngineConstants.MAXIMUM_LOAN_PERIOD)) {
            throw new InvalidLoanPeriodException("Invalid loan period!");
        }

    }

    /**
     * Verify that the age of the person taking a loan is within the correct range.
     * Ages under 18 are considered too young and ages above 76 when finishing the loan
     * are considered to old.
     * If either case happens, then throws a corresponding exception.
     *
     * @param personalCode provided personal ID code
     * @param loanPeriod Requested loan period
     * @throws InvalidAgeException If the age is not within the correct range
     */
    private void verifyAge(String personalCode, int loanPeriod) throws InvalidAgeException {
        int yearOfBirth = Integer.parseInt(personalCode.substring(1, 3));
        final int monthOfBirth = Integer.parseInt(personalCode.substring(3, 5));
        final int dayOfBirth = Integer.parseInt(personalCode.substring(5, 7));

        LocalDate date = LocalDate.now();

        if (yearOfBirth > date.getYear() % 100) yearOfBirth += 1900;
        else yearOfBirth += 2000;

        LocalDate birthDate = LocalDate.of(yearOfBirth, monthOfBirth, dayOfBirth);

        if (date.isBefore(birthDate.plusYears(18)) ||
                date.plusMonths(loanPeriod).isAfter(birthDate.plusYears(DecisionEngineConstants.EXPECTED_LIFELINE))) {
            throw new InvalidAgeException("Age constraints do not allow this loan to be taken.");
        }
    }
}
