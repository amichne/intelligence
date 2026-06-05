# Interface Design for Testability

Good interfaces make testing natural:

1. **Accept dependencies, don't create them**

   ```text
   // Testable
   processOrder(order, paymentGateway)

   // Hard to test
   processOrder(order)
     gateway = createPaymentGatewayFromEnvironment()
   ```

2. **Return results, don't produce side effects**

   ```text
   // Testable
   discount = calculateDiscount(cart)

   // Hard to test
   applyDiscountByMutatingCart(cart)
   ```

3. **Small surface area**
   - Fewer methods = fewer tests needed
   - Fewer params = simpler test setup
