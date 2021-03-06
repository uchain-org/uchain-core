package com.uchain.config;

import com.uchain.util.Constants;

import java.math.BigInteger;

public class ConstantsAdapter extends Constants {
    private final Constants delegate;

    public ConstantsAdapter(Constants delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getDURATION_LIMIT() {
        return delegate.getDURATION_LIMIT();
    }

    @Override
    public BigInteger getInitialNonce() {
        return delegate.getInitialNonce();
    }

    @Override
    public int getMAXIMUM_EXTRA_DATA_SIZE() {
        return delegate.getMAXIMUM_EXTRA_DATA_SIZE();
    }

    @Override
    public int getMIN_GAS_LIMIT() {
        return delegate.getMIN_GAS_LIMIT();
    }

    @Override
    public int getGAS_LIMIT_BOUND_DIVISOR() {
        return delegate.getGAS_LIMIT_BOUND_DIVISOR();
    }

    @Override
    public BigInteger getMINIMUM_DIFFICULTY() {
        return delegate.getMINIMUM_DIFFICULTY();
    }

    @Override
    public BigInteger getDIFFICULTY_BOUND_DIVISOR() {
        return delegate.getDIFFICULTY_BOUND_DIVISOR();
    }

    @Override
    public int getEXP_DIFFICULTY_PERIOD() {
        return delegate.getEXP_DIFFICULTY_PERIOD();
    }

    @Override
    public int getUNCLE_GENERATION_LIMIT() {
        return delegate.getUNCLE_GENERATION_LIMIT();
    }

    @Override
    public int getUNCLE_LIST_LIMIT() {
        return delegate.getUNCLE_LIST_LIMIT();
    }

    @Override
    public int getBEST_NUMBER_DIFF_LIMIT() {
        return delegate.getBEST_NUMBER_DIFF_LIMIT();
    }

    @Override
    public BigInteger getBLOCK_REWARD() {
        return delegate.getBLOCK_REWARD();
    }

    @Override
    public int getMAX_CONTRACT_SZIE() {
        return delegate.getMAX_CONTRACT_SZIE();
    }

    @Override
    public boolean createEmptyContractOnOOG() {
        return delegate.createEmptyContractOnOOG();
    }

    @Override
    public boolean hasDelegateCallOpcode() {
        return delegate.hasDelegateCallOpcode();
    }

    public static BigInteger getSECP256K1N() {
        return Constants.getSECP256K1N();
    }
}
