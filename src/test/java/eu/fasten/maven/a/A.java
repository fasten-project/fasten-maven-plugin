package eu.fasten.maven.a;

import eu.fasten.maven.b.B;
import eu.fasten.maven.bc.BC;
import eu.fasten.maven.missing.Missing;

public class A
{
    public void m1()
    {
        B.mB1();
        m2();
        BC.mBC();
    }

    public void m2()
    {
        B.mB1();
        Missing.mMissing();
    }
}
