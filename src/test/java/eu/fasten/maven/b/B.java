package eu.fasten.maven.b;

import eu.fasten.maven.c.C;

public class B
{
    public static void mB1()
    {
        mBi();
    }

    private static void mBi()
    {
        C.mC1();
    }

    public static void mB2()
    {
        C.mC2();
    }
}
