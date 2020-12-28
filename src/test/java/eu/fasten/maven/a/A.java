package eu.fasten.maven.a;

import eu.fasten.maven.b.B;

public class A
{
    public void m1()
    {
        B.mB1();
    }

    public void m2()
    {
        m1();
    }
}
